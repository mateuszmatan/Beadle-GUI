package com.bbh.itss.beadle.config

import com.bbh.itss.beadle.application.UseCase
import com.bbh.itss.beadle.application.audit.port.out.ActionHistoryPort
import com.bbh.itss.beadle.application.audit.port.out.ObjectKeyPort
import com.bbh.itss.beadle.application.catalog.port.in.DepartmentsUseCase
import com.bbh.itss.beadle.application.catalog.port.in.ProductCommand
import com.bbh.itss.beadle.application.catalog.port.in.ProductsUseCase
import com.bbh.itss.beadle.application.catalog.port.out.DepartmentRepositoryPort
import com.bbh.itss.beadle.application.catalog.port.out.DepartmentUsagePort
import com.bbh.itss.beadle.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.beadle.application.change.port.in.ChangeIntegrations
import com.bbh.itss.beadle.application.change.port.in.ProductionChangesUseCase
import com.bbh.itss.beadle.application.change.port.out.ChangeProductsPort
import com.bbh.itss.beadle.application.change.port.out.ChangeProfileRepositoryPort
import com.bbh.itss.beadle.application.change.port.out.CyberTrackPort
import com.bbh.itss.beadle.application.change.port.out.JiraPort
import com.bbh.itss.beadle.application.change.port.out.ProTechLookupPort
import com.bbh.itss.beadle.application.change.port.out.ProductionChangeRepositoryPort
import com.bbh.itss.beadle.application.change.port.out.ProTechPort
import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort
import com.bbh.itss.beadle.domain.catalog.Department
import com.bbh.itss.beadle.domain.catalog.DepartmentUsage
import com.bbh.itss.beadle.domain.audit.ActionRecord
import com.bbh.itss.beadle.domain.catalog.Product
import com.bbh.itss.beadle.support.RecordingTransactionManager
import org.springframework.aop.framework.Advised
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.interceptor.TransactionInterceptor
import spock.lang.Specification

import java.time.Clock
import java.util.function.Supplier

import static com.bbh.itss.beadle.support.RecordingTransactionManager.transactionState
import static java.time.Clock.systemUTC
import static org.springframework.aop.support.AopUtils.isAopProxy

class UseCaseConfigurationSpec extends Specification {

    static final List<String> UNAUDITED = ['changeOptionsService', 'lookupService', 'actionHistoryService']

    def transactions = new RecordingTransactionManager()
    List<String> calls = []
    ProductRepositoryPort products = Mock()
    DepartmentRepositoryPort departments = Mock()
    DepartmentUsagePort usage = Mock()
    JiraPort jira = Mock()
    ProTechPort proTech = Mock()
    CyberTrackPort cyberTrack = Mock()
    ActionHistoryPort history = Mock() {
        record(_ as ActionRecord) >> { ActionRecord row -> calls << "audit ${row.action()} ${transactionState()}"; row }
    }

    def runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration, TransactionAutoConfiguration))
            .withUserConfiguration(UseCaseConfiguration)
            .withBean(PlatformTransactionManager, { transactions } as Supplier<PlatformTransactionManager>)
            .withBean(ProductRepositoryPort, { products } as Supplier<ProductRepositoryPort>)
            .withBean(DepartmentRepositoryPort, { departments } as Supplier<DepartmentRepositoryPort>)
            .withBean(DepartmentUsagePort, { usage } as Supplier<DepartmentUsagePort>)
            .withBean(ChangeProductsPort, { Mock(ChangeProductsPort) } as Supplier<ChangeProductsPort>)
            .withBean(ChangeProfileRepositoryPort, { Mock(ChangeProfileRepositoryPort) } as Supplier<ChangeProfileRepositoryPort>)
            .withBean(ProductionChangeRepositoryPort, { Mock(ProductionChangeRepositoryPort) } as Supplier<ProductionChangeRepositoryPort>)
            .withBean(JiraPort, { jira } as Supplier<JiraPort>)
            .withBean(ProTechPort, { proTech } as Supplier<ProTechPort>)
            .withBean(CyberTrackPort, { cyberTrack } as Supplier<CyberTrackPort>)
            .withBean(ProTechLookupPort, { Mock(ProTechLookupPort) } as Supplier<ProTechLookupPort>)
            .withBean(SignedInUserPort, { { -> 'Mateusz Matan' } as SignedInUserPort } as Supplier<SignedInUserPort>)
            .withBean(Clock, { systemUTC() } as Supplier<Clock>)
            .withBean(ActionHistoryPort, { history } as Supplier<ActionHistoryPort>)
            .withBean(ObjectKeyPort, { Mock(ObjectKeyPort) { keyOf(*_) >> Optional.empty() } } as Supplier<ObjectKeyPort>)

    def "every use case of Beadle is a bean behind a transactional proxy that audits the annotated methods inside the transaction"() {
        expect:
        runner.run { ApplicationContext context ->
            def useCases = context.getBeansWithAnnotation(UseCase)
            assert useCases.keySet() == ['departmentService', 'productService', 'changeProfileService',
                                         'productionChangeService', 'changeOptionsService', 'lookupService',
                                         'signedInUserService', 'callbackService', 'actionHistoryService'] as Set
            useCases.each { name, useCase ->
                assert isAopProxy(useCase)
                assert (useCase as Advised).advisors*.advice*.class == (name in UNAUDITED ? [TransactionInterceptor]
                        : [TransactionInterceptor, AuditInterceptor])
            }
        }
    }

    def "products are listed in a read-only transaction and created in a read-write one"() {
        given:
        departments.findAll() >> { calls << 'departments ' + transactionState(); [new Department(3L, 'Custody', 0)] }
        products.findAll() >> { calls << 'products ' + transactionState(); [] }
        products.departmentExists(3L) >> true
        products.findProductByCode(_) >> Optional.empty()
        products.findProductByName(_) >> Optional.empty()
        products.save(_ as Product) >> { Product product ->
            calls << 'save ' + transactionState()
            new Product(7L, product.details(), 0, null)
        }

        when:
        runner.run { ApplicationContext context ->
            context.getBean(ProductsUseCase).list(null)
            context.getBean(ProductsUseCase).create(new ProductCommand('CERT', 'CertScanner', 3L, null, null, null))
        }

        then:
        transactions.log == ['begin read-only', 'commit', 'begin read-write', 'commit']
        calls == ['departments read-only', 'products read-only', 'save read-write', 'departments read-write',
                  'audit PRODUCT_CREATED read-write']
    }

    def "departments are listed in a read-only transaction and created in a read-write one"() {
        given:
        usage.perDepartment() >> [1L: departmentUsage(2)]
        usage.unused() >> departmentUsage(0)
        departments.findAll() >> { calls << 'find all ' + transactionState(); [new Department(1L, 'AI Lab', 0)] }
        departments.findByName(_) >> Optional.empty()
        departments.save(_ as Department) >> { Department department ->
            calls << 'save ' + transactionState()
            new Department(1L, department.name(), 0)
        }

        when:
        runner.run { ApplicationContext context ->
            context.getBean(DepartmentsUseCase).list()
            context.getBean(DepartmentsUseCase).create('Treasury')
        }

        then:
        transactions.log == ['begin read-only', 'commit', 'begin read-write', 'commit']
        calls == ['find all read-only', 'save read-write', 'find all read-write', 'audit DEPARTMENT_CREATED read-write']
    }

    def "a refused department deletion rolls the transaction back"() {
        given:
        usage.perDepartment() >> [1L: departmentUsage(2)]
        usage.unused() >> departmentUsage(0)
        departments.findAll() >> { calls << 'find all ' + transactionState(); [new Department(1L, 'AI Lab', 0)] }

        when:
        runner.run { ApplicationContext context ->
            try {
                context.getBean(DepartmentsUseCase).delete(1L)
            } catch (IllegalStateException ignored) {
                calls << 'refused'
            }
        }

        then:
        transactions.log == ['begin read-write', 'suspend', 'begin read-write', 'commit', 'resume', 'rollback']
        calls == ['find all read-write', 'audit DEPARTMENT_DELETED read-write', 'refused']
        0 * departments.delete(_)
    }

    def "Beadle asks Jira, ProTech and CyberTrack whether they are connected with no transaction"() {
        given:
        ChangeIntegrations integrations = null
        jira.connected() >> { calls << 'Jira ' + transactionState(); true }
        proTech.connected() >> { calls << 'ProTech ' + transactionState(); false }
        cyberTrack.connected() >> { calls << 'CyberTrack ' + transactionState(); true }

        when:
        runner.run { ApplicationContext context ->
            integrations = context.getBean(ProductionChangesUseCase).integrations()
        }

        then:
        integrations == new ChangeIntegrations(true, false, true)
        calls == ['Jira without transaction', 'ProTech without transaction', 'CyberTrack without transaction']
        transactions.log == []
    }

    private static DepartmentUsage departmentUsage(long products) {
        [productCount: { -> products }] as DepartmentUsage
    }
}
