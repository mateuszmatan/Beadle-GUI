package com.bbh.itss.beadle.adapter

import com.bbh.itss.beadle.application.AuditNotes
import com.bbh.itss.beadle.application.CallContext
import com.bbh.itss.beadle.application.audit.port.out.ActionHistoryPort
import com.bbh.itss.beadle.application.audit.port.out.ObjectKeyPort
import com.bbh.itss.beadle.application.callback.port.in.CallbacksUseCase
import com.bbh.itss.beadle.application.catalog.port.in.BusinessServicesUseCase
import com.bbh.itss.beadle.application.catalog.port.in.ProductCommand
import com.bbh.itss.beadle.application.catalog.port.in.ProductsUseCase
import com.bbh.itss.beadle.application.catalog.port.out.BusinessServiceRepositoryPort
import com.bbh.itss.beadle.application.catalog.port.out.BusinessServiceUsagePort
import com.bbh.itss.beadle.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.beadle.application.change.port.in.ChangeEditCommand
import com.bbh.itss.beadle.application.change.port.in.ChangeTasksCommand
import com.bbh.itss.beadle.application.change.port.in.ProductionChangesUseCase
import com.bbh.itss.beadle.application.change.port.in.ReminderCommand
import com.bbh.itss.beadle.application.change.port.out.ChangeProductsPort
import com.bbh.itss.beadle.application.change.port.out.ChangeProfileRepositoryPort
import com.bbh.itss.beadle.application.change.port.out.CyberTrackPort
import com.bbh.itss.beadle.application.change.port.out.JiraPort
import com.bbh.itss.beadle.application.change.port.out.ProTechLookupPort
import com.bbh.itss.beadle.application.change.port.out.ProTechPort
import com.bbh.itss.beadle.application.change.port.out.ProductionChangeRepositoryPort
import com.bbh.itss.beadle.application.user.port.in.SignedInUserUseCase
import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort
import com.bbh.itss.beadle.config.UseCaseConfiguration
import com.bbh.itss.beadle.domain.audit.ActionRecord
import com.bbh.itss.beadle.domain.callback.InternalCallback
import com.bbh.itss.beadle.domain.callback.ProTechCallback
import com.bbh.itss.beadle.domain.catalog.BusinessService
import com.bbh.itss.beadle.domain.catalog.BusinessServiceUsage
import com.bbh.itss.beadle.domain.change.ChangeTask
import com.bbh.itss.beadle.domain.change.ProductionChange
import com.bbh.itss.beadle.domain.change.WorkflowStep
import com.bbh.itss.beadle.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.support.ChangeFixtures
import com.bbh.itss.beadle.support.RecordingTransactionManager
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionSystemException
import spock.lang.Specification
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.function.Supplier

import static com.bbh.itss.beadle.domain.audit.Action.BUSINESS_SERVICE_CREATED
import static com.bbh.itss.beadle.domain.audit.Action.BUSINESS_SERVICE_DELETED
import static com.bbh.itss.beadle.domain.audit.Action.CALLBACK_INTERNAL
import static com.bbh.itss.beadle.domain.audit.Action.CALLBACK_PROTECH
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_EDITED
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_SYNCED
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_TASKS_CREATED
import static com.bbh.itss.beadle.domain.audit.Action.PRODUCT_CREATED
import static com.bbh.itss.beadle.domain.audit.Action.REMINDER_SENT
import static com.bbh.itss.beadle.domain.audit.Action.USER_SIGNED_IN
import static com.bbh.itss.beadle.domain.audit.ActionOutcome.FAILURE
import static com.bbh.itss.beadle.domain.audit.ActionOutcome.SUCCESS
import static com.bbh.itss.beadle.domain.audit.ObjectType.BUSINESS_SERVICE
import static com.bbh.itss.beadle.domain.audit.ObjectType.CHANGE
import static com.bbh.itss.beadle.domain.audit.ObjectType.PRODUCT
import static com.bbh.itss.beadle.domain.audit.ObjectType.TICKET
import static com.bbh.itss.beadle.domain.audit.ObjectType.USER
import static com.bbh.itss.beadle.domain.callback.InternalSystem.CYBERTRACK
import static com.bbh.itss.beadle.domain.callback.ProTechEvent.STATE_CHANGED
import static com.bbh.itss.beadle.domain.change.ApprovalRole.BUSINESS
import static com.bbh.itss.beadle.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.beadle.domain.change.ChangeState.CLOSED
import static com.bbh.itss.beadle.domain.change.ChangeState.DRAFT
import static com.bbh.itss.beadle.support.ChangeFixtures.CREATED
import static com.bbh.itss.beadle.support.ChangeFixtures.issued
import static com.bbh.itss.beadle.support.ChangeFixtures.releaseTask
import static com.bbh.itss.beadle.support.ChangeFixtures.template
import static com.bbh.itss.beadle.support.RecordingTransactionManager.transactionState

class AuditInterceptorSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-15T09:00:00Z')
    static final String CORRELATION_ID = '4f1c2a8e-7b3d-4e5f-9a6b-1c2d3e4f5a6b'

    def transactions = new RecordingTransactionManager()
    List<ActionRecord> rows = []
    String signedIn = 'Mateusz Matan'
    BusinessServiceRepositoryPort businessServices = Mock()
    BusinessServiceUsagePort usage = Mock() {
        unused() >> businessServiceUsage(0)
    }
    ProductRepositoryPort products = Mock()
    ProductionChangeRepositoryPort changes = Mock()
    ProTechPort proTech = Mock()
    ChangeProductsPort changeProducts = Mock()
    ChangeProfileRepositoryPort profiles = Mock()
    ObjectKeyPort keys = Mock()
    JsonMapper strict = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build()
    ActionHistoryPort history = Mock() {
        record(_ as ActionRecord) >> { ActionRecord row ->
            transactions.log << 'audit row ' + transactionState()
            rows << row
            row.toBuilder().id(rows.size() as Long).build()
        }
    }

    def runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration, TransactionAutoConfiguration))
            .withUserConfiguration(UseCaseConfiguration)
            .withBean(PlatformTransactionManager, { transactions } as Supplier<PlatformTransactionManager>)
            .withBean(ActionHistoryPort, { history } as Supplier<ActionHistoryPort>)
            .withBean(ObjectKeyPort, { keys } as Supplier<ObjectKeyPort>)
            .withBean(JsonMapper, { JsonMapper.builder().build() } as Supplier<JsonMapper>)
            .withBean(ProductRepositoryPort, { products } as Supplier<ProductRepositoryPort>)
            .withBean(BusinessServiceRepositoryPort, { businessServices } as Supplier<BusinessServiceRepositoryPort>)
            .withBean(BusinessServiceUsagePort, { usage } as Supplier<BusinessServiceUsagePort>)
            .withBean(ChangeProductsPort, { changeProducts } as Supplier<ChangeProductsPort>)
            .withBean(ChangeProfileRepositoryPort, { profiles } as Supplier<ChangeProfileRepositoryPort>)
            .withBean(ProductionChangeRepositoryPort, { changes } as Supplier<ProductionChangeRepositoryPort>)
            .withBean(JiraPort, { Mock(JiraPort) } as Supplier<JiraPort>)
            .withBean(ProTechPort, { proTech } as Supplier<ProTechPort>)
            .withBean(CyberTrackPort, { Mock(CyberTrackPort) } as Supplier<CyberTrackPort>)
            .withBean(ProTechLookupPort, { Mock(ProTechLookupPort) } as Supplier<ProTechLookupPort>)
            .withBean(SignedInUserPort, { { -> signedIn } as SignedInUserPort } as Supplier<SignedInUserPort>)
            .withBean(Clock, { Clock.fixed(NOW, ZoneOffset.UTC) } as Supplier<Clock>)

    def "a use case that succeeds writes one SUCCESS row inside its own read-write transaction"() {
        given:
        businessServices.findByName('Treasury') >> Optional.empty()
        businessServices.save(_ as BusinessService) >> new BusinessService(4L, 'Treasury', 0)
        businessServices.findAll() >> [new BusinessService(4L, 'Treasury', 0)]
        usage.perBusinessService() >> [:]

        when:
        def created = use(BusinessServicesUseCase) { it.create('Treasury') }

        then:
        created.id() == 4L
        transactions.log == ['begin read-write', 'audit row read-write', 'commit']
        rows.size() == 1
        with(rows[0]) {
            at() == NOW
            user() == 'Mateusz Matan'
            action() == BUSINESS_SERVICE_CREATED
            objectType() == BUSINESS_SERVICE
            objectId() == 4L
            objectKey() == 'Treasury'
            details() == '{"name":"Treasury"}'
            outcome() == SUCCESS
            message() == null
            CallContext.isCorrelationId(correlationId())
            durationMs() >= 0
        }
        0 * keys._
    }

    def "a use case that throws leaves a FAILURE row written in a transaction of its own while its transaction rolls back"() {
        given:
        businessServices.findAll() >> [new BusinessService(1L, 'AI Lab', 0)]
        usage.perBusinessService() >> [1L: businessServiceUsage(2)]

        when:
        use(BusinessServicesUseCase) { it.delete(1L) }

        then:
        def refused = thrown(IllegalStateException)
        transactions.log == ['begin read-write', 'suspend', 'begin read-write', 'audit row read-write', 'commit',
                             'resume', 'rollback']
        rows*.outcome() == [FAILURE]
        with(rows[0]) {
            action() == BUSINESS_SERVICE_DELETED
            objectType() == BUSINESS_SERVICE
            objectId() == 1L
            objectKey() == 'AI Lab'
            details() == '{"id":1}'
            message() == refused.message
            message() == 'AI Lab still has 2 product(s). Move them to another business service first.'
        }
        1 * keys.keyOf(BUSINESS_SERVICE, 1L) >> Optional.of('AI Lab')
        0 * businessServices.delete(_)
    }

    def "a deleted object keeps its key, read before the use case removed it"() {
        given:
        businessServices.findAll() >> [new BusinessService(5L, 'Treasury', 0)]
        usage.perBusinessService() >> [:]
        List<String> order = []

        when:
        use(BusinessServicesUseCase) { it.delete(5L) }

        then:
        1 * keys.keyOf(BUSINESS_SERVICE, 5L) >> { order << 'key'; Optional.of('Treasury') }
        1 * businessServices.delete(5L) >> { order << 'delete' }
        order == ['key', 'delete']
        rows*.objectKey() == ['Treasury']
        rows*.outcome() == [SUCCESS]
    }

    def "a SUCCESS row whose transaction does not commit is followed by a FAILURE row of its own"() {
        given:
        transactions.commitsToRefuse = 1
        businessServices.findByName('Treasury') >> Optional.empty()
        businessServices.save(_ as BusinessService) >> new BusinessService(4L, 'Treasury', 0)
        businessServices.findAll() >> [new BusinessService(4L, 'Treasury', 0)]
        usage.perBusinessService() >> [:]

        when:
        use(BusinessServicesUseCase) { it.create('Treasury') }

        then:
        thrown(TransactionSystemException)
        transactions.log == ['begin read-write', 'audit row read-write', 'commit refused', 'suspend',
                             'begin read-write', 'audit row read-write', 'commit', 'resume']
        rows*.outcome() == [SUCCESS, FAILURE]
        rows[1].message() == AuditInterceptor.ROLLED_BACK
        rows[1].toBuilder().outcome(SUCCESS).message(null).build() == rows[0]
    }

    def "a use case without a transaction writes its row in a transaction of its own, as the system that called back"() {
        given:
        def callback = new InternalCallback(CYBERTRACK, 'SCP-123', 'Done', NOW)

        when:
        use(CallbacksUseCase) { useCase ->
            try (def system = CallContext.actingAs('system:internal');
                 def correlation = CallContext.correlatedBy(CORRELATION_ID)) {
                useCase.internal(callback)
            }
        }

        then:
        transactions.log == ['begin read-write', 'audit row read-write', 'commit']
        with(rows[0]) {
            user() == 'system:internal'
            action() == CALLBACK_INTERNAL
            objectType() == TICKET
            objectId() == null
            objectKey() == 'SCP-123'
            correlationId() == CORRELATION_ID
            details() == '{"callback":{"system":"CYBERTRACK","key":"SCP-123","status":"Done",' +
                    '"at":"2026-10-15T09:00:00Z"}}'
            outcome() == SUCCESS
        }
    }

    def "a use case without a transaction that fails still leaves its FAILURE row"() {
        given:
        changes.findByNumber('CHG0031001') >> Optional.of(issued())
        proTech.read(_) >> { throw new UncheckedIOException('Beadle-ProTech did not answer', new IOException()) }

        when:
        use(CallbacksUseCase) { useCase ->
            try (def system = CallContext.actingAs('system:protech')) {
                useCase.proTech(new ProTechCallback('CHG0031001', STATE_CHANGED, BUSINESS_APPROVAL, NOW))
            }
        }

        then:
        thrown(UncheckedIOException)
        transactions.log == ['begin read-write', 'audit row read-write', 'commit']
        with(rows[0]) {
            user() == 'system:protech'
            action() == CALLBACK_PROTECH
            objectType() == CHANGE
            objectId() == null
            objectKey() == 'CHG0031001'
            outcome() == FAILURE
            message() == 'Beadle-ProTech did not answer'
        }
    }

    def "a callback on a change Beadle created is filed under the change it read again"() {
        given:
        changes.findByNumber('CHG0031001') >> Optional.of(issued())
        proTech.read(_) >> [CHG0031001: issued()]

        when:
        use(CallbacksUseCase) { it.proTech(new ProTechCallback('CHG0031001', STATE_CHANGED, DRAFT, NOW)) }

        then:
        rows.collect { [it.objectType(), it.objectId(), it.objectKey(), it.outcome()] } ==
                [[CHANGE, 7L, 'CHG0031001', SUCCESS]]
    }

    def "opening a change is recorded only when it pulled a newer state from ProTech"() {
        given:
        changes.load(7L) >> Optional.of(issued())
        changes.save(_) >> { it[0] }

        when:
        use(ProductionChangesUseCase) { it.get(7L) }

        then:
        1 * proTech.read(_) >> [CHG0031001: issued()]
        rows == []
        transactions.log == []

        when:
        use(ProductionChangesUseCase) { it.get(7L) }

        then:
        1 * proTech.read(_) >> [CHG0031001: issued(state: BUSINESS_APPROVAL,
                workflow: [new WorkflowStep(DRAFT, CREATED), new WorkflowStep(BUSINESS_APPROVAL, NOW)])]
        rows.collect { [it.action(), it.objectType(), it.objectId(), it.objectKey(), it.details()] } ==
                [[CHANGE_SYNCED, CHANGE, 7L, 'CHG0031001', '{"id":7}']]
    }

    def "listing the changes records a CHANGE_SYNCED row for each change whose newer state it stored, and none for the rest"() {
        given:
        def moved = [state: BUSINESS_APPROVAL,
                     workflow: [new WorkflowStep(DRAFT, CREATED), new WorkflowStep(BUSINESS_APPROVAL, NOW)]]
        changes.findByBusinessService(3L) >> [issued(), issued(id: 8L, number: 'CHG0031002'),
                                         issued(id: 9L, number: 'CHG0031003')]
        changes.save(_) >> { it[0] }

        when:
        use(ProductionChangesUseCase) { it.list(3L) }

        then:
        1 * proTech.read(_) >> [CHG0031001: issued(moved), CHG0031002: issued(id: 8L, number: 'CHG0031002'),
                                CHG0031003: issued(moved + [id: 9L, number: 'CHG0031003'])]
        rows.collect { [it.action(), it.objectType(), it.objectId(), it.objectKey(), it.outcome(), it.details()] } == [
                [CHANGE_SYNCED, CHANGE, 7L, 'CHG0031001', SUCCESS, '{"businessServiceId":3}'],
                [CHANGE_SYNCED, CHANGE, 9L, 'CHG0031003', SUCCESS, '{"businessServiceId":3}']]
        rows*.user() == ['Mateusz Matan', 'Mateusz Matan']
        transactions.log == ['begin read-write', 'audit row read-write', 'commit'] * 2

        when:
        rows.clear()
        use(ProductionChangesUseCase) { it.list(3L) }

        then:
        1 * proTech.read(_) >> [CHG0031001: issued(), CHG0031002: issued(id: 8L, number: 'CHG0031002'),
                                CHG0031003: issued(id: 9L, number: 'CHG0031003')]
        rows == []
    }

    def "a list that fails while storing a newer state records the changes it stored and the one it failed on"() {
        given:
        def moved = [state: BUSINESS_APPROVAL,
                     workflow: [new WorkflowStep(DRAFT, CREATED), new WorkflowStep(BUSINESS_APPROVAL, NOW)]]
        changes.findAll() >> [issued(), issued(id: 8L, number: 'CHG0031002')]
        proTech.read(_) >> [CHG0031001: issued(moved), CHG0031002: issued(moved + [id: 8L, number: 'CHG0031002'])]
        changes.save(_) >> { it[0] } >> { throw new IllegalArgumentException('The database is full') }

        when:
        use(ProductionChangesUseCase) { it.list(null) }

        then:
        thrown(IllegalArgumentException)
        rows.collect { [it.action(), it.objectKey(), it.outcome(), it.message()] } == [
                [CHANGE_SYNCED, 'CHG0031001', SUCCESS, null],
                [CHANGE_SYNCED, 'CHG0031002', FAILURE, 'The database is full']]
    }

    def "#refusal after storing the newer state ProTech holds records that state as synced before its FAILURE row"() {
        given:
        keys.keyOf(CHANGE, 7L) >> Optional.of('CHG0031001')
        changes.load(7L) >> Optional.of(issued())
        changes.save(_) >> { ProductionChange it -> it.toBuilder().version(1L).build() }
        proTech.read(_) >> [CHG0031001: remote]

        when:
        use(ProductionChangesUseCase, call)

        then:
        thrown(RuntimeException)
        rows.collect { [it.action(), it.objectType(), it.objectId(), it.objectKey(), it.outcome(), it.message()] } == [
                [CHANGE_SYNCED, CHANGE, 7L, 'CHG0031001', SUCCESS, null],
                [action, CHANGE, 7L, 'CHG0031001', FAILURE, message]]
        rows*.details().every { it.startsWith(details) }
        rows[0].details() == rows[1].details()
        rows[0].correlationId() != null
        rows[0].correlationId() == rows[1].correlationId()
        transactions.log == ['begin read-write', 'audit row read-write', 'commit'] * 2
        0 * proTech.remind(*_)
        0 * proTech.update(_)

        where:
        refusal                                     | remote                | call                                                             || action        | message                                                                  | details
        'a reminder of a change ProTech closed'     | issued(state: CLOSED) | { it.remind(7L, new ReminderCommand(3L, BUSINESS, null)) }       || REMINDER_SENT | 'CHG0031001 is closed in ProTech, so nobody is reminded to approve it'   | '{"id":7,"command":{"businessServiceId":3,"approval":"BUSINESS","task":null}}'
        'a reminder of a task the change lacks'     | moved()               | { it.remind(7L, new ReminderCommand(3L, null, 'CTASK0049999')) } || REMINDER_SENT | 'task CTASK0049999 is not a change task of CHG0031001'                   | '{"id":7,"command":{"businessServiceId":3,"approval":null,"task":"CTASK0049999"}}'
        'an edit of a change ProTech closed'        | issued(state: CLOSED) | { it.update(7L, unchangedEdit()) }                               || CHANGE_EDITED | 'CHG0031001 is closed in ProTech and can no longer be changed'           | '{"id":7,"command":{"version":0,"businessServiceId":3,"shortDescription":'
        'change tasks for a change ProTech closed'  | issued(state: CLOSED) | { it.createTasks(7L, new ChangeTasksCommand(0L, 3L, [releaseTask([:], null)])) } || CHANGE_TASKS_CREATED | 'CHG0031001 is closed in ProTech and can no longer be changed' | '{"id":7,"command":{"version":0,"businessServiceId":3,"tasks":['
    }

    def "a reminder refused before ProTech is read leaves only its FAILURE row and the change as it was"() {
        given:
        keys.keyOf(CHANGE, 7L) >> Optional.of('CHG0031001')
        changes.load(7L) >> Optional.of(issued())

        when:
        use(ProductionChangesUseCase) { it.remind(7L, new ReminderCommand(3L, BUSINESS, 'CTASK0041002')) }

        then:
        thrown(InvalidRequestException)
        rows.collect { [it.action(), it.objectKey(), it.outcome(), it.message()] } == [
                [REMINDER_SENT, 'CHG0031001', FAILURE, 'task Remind the approvers of either the change or one change task']]
        0 * proTech._
        0 * changes.save(_)
    }

    def "an action that succeeds records only its own row, also when it stored the newer state on its way"() {
        given:
        changes.load(7L) >> Optional.of(issued())
        changes.save(_) >> { ProductionChange it -> it.toBuilder().version(it.version() + 1).build() }
        proTech.read(_) >> [CHG0031001: moved()]
        proTech.remind('CHG0031001', _) >> ['Olivia Bennett']

        when:
        use(ProductionChangesUseCase) { it.remind(7L, new ReminderCommand(3L, BUSINESS, null)) }

        then:
        rows.collect { [it.action(), it.objectKey(), it.outcome()] } == [[REMINDER_SENT, 'CHG0031001', SUCCESS]]
    }

    def "an invalid request is recorded with its problems, its object named by the arguments and its details"() {
        given:
        def name = 'N' * 40_000

        when:
        use(ProductsUseCase) { it.create(new ProductCommand('CERT', name, null, null, null, null)) }

        then:
        thrown(InvalidRequestException)
        with(rows[0]) {
            action() == PRODUCT_CREATED
            outcome() == FAILURE
            objectType() == PRODUCT
            objectId() == null
            objectKey() == 'CERT'
            message().startsWith('name is too long')
            message().endsWith("; businessServiceId choose the product's business service")
            details() == '{"command":{"code":"CERT","name":"' + name + '","businessServiceId":null,"ownerTeam":null,' +
                    '"contactEmail":null,"version":null}}'
        }
    }

    def "a business service that could not be created is filed under the name it was to have"() {
        given:
        businessServices.findByName('Treasury') >> Optional.of(new BusinessService(4L, 'Treasury', 0))

        when:
        use(BusinessServicesUseCase) { it.create(' Treasury ') }

        then:
        thrown(IllegalStateException)
        rows.collect { [it.action(), it.objectType(), it.objectId(), it.objectKey(), it.outcome()] } ==
                [[BUSINESS_SERVICE_CREATED, BUSINESS_SERVICE, null, 'Treasury', FAILURE]]
        0 * keys._
    }

    def "the change tasks ProTech created stand last in the details of the row, also when it refused one of them"() {
        given:
        changes.load(7L) >> Optional.of(issued(tasks: []))
        changes.save(_) >> { it[0] }
        keys.keyOf(CHANGE, 7L) >> Optional.of('CHG0031001')
        proTech.read(_) >> [CHG0031001: issued(tasks: [])]
        proTech.createTask('CHG0031001', _) >> 'CTASK0050001' >> {
            if (refused) {
                throw new IllegalStateException('ProTech refused')
            }
            'CTASK0050002'
        }

        when:
        try {
            use(ProductionChangesUseCase) {
                it.createTasks(7L, new ChangeTasksCommand(0L, 3L, [releaseTask([:], null), ChangeTask.of(ChangeFixtures.details(2))]))
            }
        } catch (IllegalStateException expected) {
            assert refused
        }

        then:
        rows.size() == 1
        with(rows[0]) {
            action() == CHANGE_TASKS_CREATED
            objectType() == CHANGE
            objectKey() == 'CHG0031001'
            outcome() == (refused ? FAILURE : SUCCESS)
            details().startsWith('{"id":7,"command":{"version":0,"businessServiceId":3,"tasks":[')
            details().endsWith(',"result":{"created":' + created + '}}')
        }

        where:
        refused || created
        false   || '["CTASK0050001","CTASK0050002"]'
        true    || '["CTASK0050001"]'
    }

    def "arguments too long for the details give way to their length, and the result stays whole after them"() {
        given:
        def interceptor = new AuditInterceptor(null, null, Stub(ObjectProvider) {
            getIfUnique(_) >> JsonMapper.builder().build()
        }, null, null)
        def notes = AuditNotes.listen()
        AuditNotes.result('reminded', [[role: 'SUPPORT', task: null, people: ['Jane Smith'], token: 'secret']])
        AuditNotes.created('SCP-1002')
        notes.close()
        def result = ',"result":{"reminded":[{"role":"SUPPORT","task":null,"people":["Jane Smith"],' +
                '"token":"*****"}],"created":["SCP-1002"]}}'
        def command = '{"command":{"name":"' + 'N' * ActionRecord.DETAILS_MAX + '","code":"CERT"}'

        when:
        def details = interceptor.details(arguments(command + '}'), notes)

        then:
        details == '{"truncated":true,"bytes":' + (command + result).length() + result
        strict.readTree(details).get('result').get('created').collect { it.stringValue() } == ['SCP-1002']

        expect:
        interceptor.details(arguments('{}'), notes) == '{' + result.substring(1)
        interceptor.details(null, notes) == '{' + result.substring(1)
        interceptor.details(arguments('{"id":7}'), AuditNotes.listen().withCloseable { it }) == '{"id":7}'
        interceptor.details(arguments('{"id":7}'), null) == '{"id":7}'
        interceptor.details(null, null) == null
    }

    def "a signed-in user that cannot be read is recorded as unknown"() {
        given:
        signedIn = null

        when:
        def user = use(SignedInUserUseCase) { it.signIn() }

        then:
        user.name() == null
        rows.collect { [it.user(), it.action(), it.objectType()] } == [['unknown', USER_SIGNED_IN, USER]]
    }

    def "properties that look like secrets are masked at every depth"() {
        given:
        def mapper = JsonMapper.builder().build()
        def tree = mapper.readTree('''{"name": "CertScanner", "serviceToken": "t0k3n", "nested": {"clientSecret": "s",
                "password": null, "list": [{"Authorization": "Bearer x", "apiKey": "k", "keep": "visible"}]}}''')

        expect:
        mapper.writeValueAsString(Secrets.masked('request', tree)) ==
                '{"name":"CertScanner","serviceToken":"*****","nested":{"clientSecret":"*****","password":null,' +
                '"list":[{"Authorization":"*****","apiKey":"*****","keep":"visible"}]}}'
        mapper.writeValueAsString(Secrets.masked('token', mapper.readTree('"plain"'))) == '"*****"'
    }

    private ObjectNode arguments(String json) {
        strict.readTree(json) as ObjectNode
    }

    /** The created change as ProTech moved it on to Business Approval. */
    private static ProductionChange moved() {
        issued(state: BUSINESS_APPROVAL,
                workflow: [new WorkflowStep(DRAFT, CREATED), new WorkflowStep(BUSINESS_APPROVAL, NOW)])
    }

    /** An edit of the created change that sends it as it is. */
    private static ChangeEditCommand unchangedEdit() {
        def change = issued()
        new ChangeEditCommand(0L, 3L, change.shortDescription(), change.description(), change.schedule(),
                change.template(), change.tasks())
    }

    private <T, R> R use(Class<T> useCase, Closure<R> work) {
        R result = null
        runner.run { ApplicationContext context -> result = work(context.getBean(useCase)) }
        result
    }

    private static BusinessServiceUsage businessServiceUsage(long products) {
        [productCount: { -> products }, deletionRefusal: { String name -> Optional.empty() }] as BusinessServiceUsage
    }
}
