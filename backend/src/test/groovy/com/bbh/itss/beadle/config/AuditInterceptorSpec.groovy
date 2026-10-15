package com.bbh.itss.beadle.config

import com.bbh.itss.beadle.application.CallContext
import com.bbh.itss.beadle.application.audit.port.out.ActionHistoryPort
import com.bbh.itss.beadle.application.audit.port.out.ObjectKeyPort
import com.bbh.itss.beadle.application.callback.port.in.CallbacksUseCase
import com.bbh.itss.beadle.application.catalog.port.in.DepartmentsUseCase
import com.bbh.itss.beadle.application.catalog.port.in.ProductCommand
import com.bbh.itss.beadle.application.catalog.port.in.ProductsUseCase
import com.bbh.itss.beadle.application.catalog.port.out.DepartmentRepositoryPort
import com.bbh.itss.beadle.application.catalog.port.out.DepartmentUsagePort
import com.bbh.itss.beadle.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.beadle.application.change.port.in.ProductionChangesUseCase
import com.bbh.itss.beadle.application.change.port.out.ChangeProductsPort
import com.bbh.itss.beadle.application.change.port.out.ChangeProfileRepositoryPort
import com.bbh.itss.beadle.application.change.port.out.CyberTrackPort
import com.bbh.itss.beadle.application.change.port.out.JiraPort
import com.bbh.itss.beadle.application.change.port.out.ProTechLookupPort
import com.bbh.itss.beadle.application.change.port.out.ProTechPort
import com.bbh.itss.beadle.application.change.port.out.ProductionChangeRepositoryPort
import com.bbh.itss.beadle.application.user.port.in.SignedInUserUseCase
import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort
import com.bbh.itss.beadle.domain.audit.ActionRecord
import com.bbh.itss.beadle.domain.callback.InternalCallback
import com.bbh.itss.beadle.domain.callback.ProTechCallback
import com.bbh.itss.beadle.domain.catalog.Department
import com.bbh.itss.beadle.domain.catalog.DepartmentUsage
import com.bbh.itss.beadle.domain.change.WorkflowStep
import com.bbh.itss.beadle.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.support.RecordingTransactionManager
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionSystemException
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.function.Supplier

import static com.bbh.itss.beadle.domain.audit.Action.CALLBACK_INTERNAL
import static com.bbh.itss.beadle.domain.audit.Action.CALLBACK_PROTECH
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_SYNCED
import static com.bbh.itss.beadle.domain.audit.Action.DEPARTMENT_CREATED
import static com.bbh.itss.beadle.domain.audit.Action.DEPARTMENT_DELETED
import static com.bbh.itss.beadle.domain.audit.Action.PRODUCT_CREATED
import static com.bbh.itss.beadle.domain.audit.Action.USER_SIGNED_IN
import static com.bbh.itss.beadle.domain.audit.ActionOutcome.FAILURE
import static com.bbh.itss.beadle.domain.audit.ActionOutcome.SUCCESS
import static com.bbh.itss.beadle.domain.audit.ObjectType.CHANGE
import static com.bbh.itss.beadle.domain.audit.ObjectType.DEPARTMENT
import static com.bbh.itss.beadle.domain.audit.ObjectType.PRODUCT
import static com.bbh.itss.beadle.domain.audit.ObjectType.TICKET
import static com.bbh.itss.beadle.domain.audit.ObjectType.USER
import static com.bbh.itss.beadle.domain.callback.InternalSystem.CYBERTRACK
import static com.bbh.itss.beadle.domain.callback.ProTechEvent.STATE_CHANGED
import static com.bbh.itss.beadle.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.beadle.domain.change.ChangeState.DRAFT
import static com.bbh.itss.beadle.support.ChangeFixtures.RAISED
import static com.bbh.itss.beadle.support.ChangeFixtures.raised
import static com.bbh.itss.beadle.support.RecordingTransactionManager.transactionState

class AuditInterceptorSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-15T09:00:00Z')
    static final String CORRELATION_ID = '4f1c2a8e-7b3d-4e5f-9a6b-1c2d3e4f5a6b'

    def transactions = new RecordingTransactionManager()
    List<ActionRecord> rows = []
    String signedIn = 'Mateusz Matan'
    DepartmentRepositoryPort departments = Mock()
    DepartmentUsagePort usage = Mock() {
        unused() >> departmentUsage(0)
    }
    ProductRepositoryPort products = Mock()
    ProductionChangeRepositoryPort changes = Mock()
    ProTechPort proTech = Mock()
    ObjectKeyPort keys = Mock()
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
            .withBean(DepartmentRepositoryPort, { departments } as Supplier<DepartmentRepositoryPort>)
            .withBean(DepartmentUsagePort, { usage } as Supplier<DepartmentUsagePort>)
            .withBean(ChangeProductsPort, { Mock(ChangeProductsPort) } as Supplier<ChangeProductsPort>)
            .withBean(ChangeProfileRepositoryPort, { Mock(ChangeProfileRepositoryPort) } as Supplier<ChangeProfileRepositoryPort>)
            .withBean(ProductionChangeRepositoryPort, { changes } as Supplier<ProductionChangeRepositoryPort>)
            .withBean(JiraPort, { Mock(JiraPort) } as Supplier<JiraPort>)
            .withBean(ProTechPort, { proTech } as Supplier<ProTechPort>)
            .withBean(CyberTrackPort, { Mock(CyberTrackPort) } as Supplier<CyberTrackPort>)
            .withBean(ProTechLookupPort, { Mock(ProTechLookupPort) } as Supplier<ProTechLookupPort>)
            .withBean(SignedInUserPort, { { -> signedIn } as SignedInUserPort } as Supplier<SignedInUserPort>)
            .withBean(Clock, { Clock.fixed(NOW, ZoneOffset.UTC) } as Supplier<Clock>)

    def "a use case that succeeds writes one SUCCESS row inside its own read-write transaction"() {
        given:
        departments.findByName('Treasury') >> Optional.empty()
        departments.save(_ as Department) >> new Department(4L, 'Treasury', 0)
        departments.findAll() >> [new Department(4L, 'Treasury', 0)]
        usage.perDepartment() >> [:]

        when:
        def created = use(DepartmentsUseCase) { it.create('Treasury') }

        then:
        created.id() == 4L
        transactions.log == ['begin read-write', 'audit row read-write', 'commit']
        rows.size() == 1
        with(rows[0]) {
            at() == NOW
            user() == 'Mateusz Matan'
            action() == DEPARTMENT_CREATED
            objectType() == DEPARTMENT
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
        departments.findAll() >> [new Department(1L, 'AI Lab', 0)]
        usage.perDepartment() >> [1L: departmentUsage(2)]

        when:
        use(DepartmentsUseCase) { it.delete(1L) }

        then:
        def refused = thrown(IllegalStateException)
        transactions.log == ['begin read-write', 'suspend', 'begin read-write', 'audit row read-write', 'commit',
                             'resume', 'rollback']
        rows*.outcome() == [FAILURE]
        with(rows[0]) {
            action() == DEPARTMENT_DELETED
            objectType() == DEPARTMENT
            objectId() == 1L
            objectKey() == 'AI Lab'
            details() == '{"id":1}'
            message() == refused.message
            message() == 'AI Lab still has 2 product(s). Move them to another department first.'
        }
        1 * keys.keyOf(DEPARTMENT, 1L) >> Optional.of('AI Lab')
        0 * departments.delete(_)
    }

    def "a deleted object keeps its key, read before the use case removed it"() {
        given:
        departments.findAll() >> [new Department(5L, 'Treasury', 0)]
        usage.perDepartment() >> [:]
        List<String> order = []

        when:
        use(DepartmentsUseCase) { it.delete(5L) }

        then:
        1 * keys.keyOf(DEPARTMENT, 5L) >> { order << 'key'; Optional.of('Treasury') }
        1 * departments.delete(5L) >> { order << 'delete' }
        order == ['key', 'delete']
        rows*.objectKey() == ['Treasury']
        rows*.outcome() == [SUCCESS]
    }

    def "a SUCCESS row whose transaction does not commit is followed by a FAILURE row of its own"() {
        given:
        transactions.commitsToRefuse = 1
        departments.findByName('Treasury') >> Optional.empty()
        departments.save(_ as Department) >> new Department(4L, 'Treasury', 0)
        departments.findAll() >> [new Department(4L, 'Treasury', 0)]
        usage.perDepartment() >> [:]

        when:
        use(DepartmentsUseCase) { it.create('Treasury') }

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
        def callback = new InternalCallback(CYBERTRACK, 'SCP-123', 'Done', NOW, CORRELATION_ID)

        when:
        use(CallbacksUseCase) { useCase ->
            try (def system = CallContext.actingAs('system:internal');
                 def correlation = CallContext.correlatedBy(callback.correlationId())) {
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
                    '"at":"2026-10-15T09:00:00Z","correlationId":"' + CORRELATION_ID + '"}}'
            outcome() == SUCCESS
        }
    }

    def "a use case without a transaction that fails still leaves its FAILURE row"() {
        given:
        changes.findByNumber('CHG0031001') >> Optional.of(raised())
        proTech.read(_) >> { throw new UncheckedIOException('Beadle-ProTech did not answer', new IOException()) }

        when:
        use(CallbacksUseCase) { useCase ->
            try (def system = CallContext.actingAs('system:protech')) {
                useCase.proTech(new ProTechCallback('CHG0031001', STATE_CHANGED, BUSINESS_APPROVAL, NOW, null))
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

    def "a callback on a change Beadle raised is filed under the change it read again"() {
        given:
        changes.findByNumber('CHG0031001') >> Optional.of(raised())
        proTech.read(_) >> [CHG0031001: raised()]

        when:
        use(CallbacksUseCase) { it.proTech(new ProTechCallback('CHG0031001', STATE_CHANGED, DRAFT, NOW, null)) }

        then:
        rows.collect { [it.objectType(), it.objectId(), it.objectKey(), it.outcome()] } ==
                [[CHANGE, 7L, 'CHG0031001', SUCCESS]]
    }

    def "opening a change is recorded only when it pulled a newer state from ProTech"() {
        given:
        changes.load(7L) >> Optional.of(raised())
        changes.save(_) >> { it[0] }

        when:
        use(ProductionChangesUseCase) { it.get(7L) }

        then:
        1 * proTech.read(_) >> [CHG0031001: raised()]
        rows == []
        transactions.log == []

        when:
        use(ProductionChangesUseCase) { it.get(7L) }

        then:
        1 * proTech.read(_) >> [CHG0031001: raised(state: BUSINESS_APPROVAL,
                workflow: [new WorkflowStep(DRAFT, RAISED), new WorkflowStep(BUSINESS_APPROVAL, NOW)])]
        rows.collect { [it.action(), it.objectType(), it.objectId(), it.objectKey(), it.details()] } ==
                [[CHANGE_SYNCED, CHANGE, 7L, 'CHG0031001', '{"id":7}']]
    }

    def "an invalid request is recorded with its problems, its object named by the arguments and its long details trimmed"() {
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
            message().endsWith("; departmentId choose the product's department")
            details().startsWith('{"command":{"code":"CERT","name":"NNN')
            details().endsWith('...')
            details().bytes.length <= ActionRecord.DETAILS_MAX
        }
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
        mapper.writeValueAsString(AuditInterceptor.masked('request', tree)) ==
                '{"name":"CertScanner","serviceToken":"*****","nested":{"clientSecret":"*****","password":null,' +
                '"list":[{"Authorization":"*****","apiKey":"*****","keep":"visible"}]}}'
        mapper.writeValueAsString(AuditInterceptor.masked('token', mapper.readTree('"plain"'))) == '"*****"'
    }

    private <T, R> R use(Class<T> useCase, Closure<R> work) {
        R result = null
        runner.run { ApplicationContext context -> result = work(context.getBean(useCase)) }
        result
    }

    private static DepartmentUsage departmentUsage(long products) {
        [productCount: { -> products }, deletionRefusal: { String name -> Optional.empty() }] as DepartmentUsage
    }
}
