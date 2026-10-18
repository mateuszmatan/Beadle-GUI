package com.bbh.itss.beadle

import com.bbh.itss.beadle.adapter.in.web.AuditedRequest
import com.bbh.itss.beadle.application.UseCase
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.Location
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import jakarta.persistence.Converter
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.MappedSuperclass
import org.springframework.context.annotation.Configuration
import org.springframework.data.repository.Repository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import spock.lang.Shared
import spock.lang.Specification

import java.util.function.Predicate

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue
import static com.tngtech.archunit.base.DescribedPredicate.describe
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

/** The hexagonal package rules of Beadle's main code. */
class ArchitectureSpec extends Specification {

    static final String DOMAIN = 'com.bbh.itss.beadle.domain..'
    static final String APPLICATION = 'com.bbh.itss.beadle.application..'
    static final String ADAPTER_IN = 'com.bbh.itss.beadle.adapter.in..'
    static final String ADAPTER_OUT = 'com.bbh.itss.beadle.adapter.out..'
    static final String WEB = 'com.bbh.itss.beadle.adapter.in.web..'
    static final String PERSISTENCE = 'com.bbh.itss.beadle.adapter.out.persistence..'
    static final String HTTP = 'com.bbh.itss.beadle.adapter.out.http..'
    static final String ADAPTER = 'com.bbh.itss.beadle.adapter..'
    /** The shared parts of the adapters, such as the audit interceptor and the record mapper. */
    static final String ADAPTER_ROOT = 'com.bbh.itss.beadle.adapter'
    static final String CONFIG = 'com.bbh.itss.beadle.config..'
    static final String PORT_OUT = 'com.bbh.itss.beadle.application..port.out..'
    static final String[] HTTP_ADAPTERS = [HTTP, 'com.bbh.itss.beadle.adapter.out.internal..',
                                           'com.bbh.itss.beadle.adapter.out.protech..']
    static final String[] HELPERS = ['org.apache.commons.lang3..', 'org.apache.commons.collections4..', 'lombok..']

    @Shared
    JavaClasses beadle = new ClassFileImporter()
            .importLocations([Location.of(BeadleApplication.protectionDomain.codeSource.location)])

    def "#rule.description"() {
        expect:
        rule.check(beadle)

        where:
        rule << rules()
    }

    private static List<ArchRule> rules() {
        [classes().that().doNotHaveFullyQualifiedName(BeadleApplication.name)
                 .should().resideInAnyPackage(DOMAIN, APPLICATION, ADAPTER, CONFIG),
         classes().that().resideInAPackage('com.bbh.itss.beadle')
                 .should().haveFullyQualifiedName(BeadleApplication.name),
         classes().that().resideInAPackage(DOMAIN)
                 .should().onlyDependOnClassesThat().resideInAnyPackage('java..', DOMAIN, *HELPERS),
         classes().that().resideInAPackage(APPLICATION)
                 .should().onlyDependOnClassesThat()
                 .resideInAnyPackage('java..', DOMAIN, APPLICATION, 'org.slf4j..', *HELPERS),
         noClasses().that().resideInAPackage(ADAPTER)
                 .should().dependOnClassesThat().resideInAPackage(CONFIG),
         noClasses().that().resideInAPackage(ADAPTER_IN)
                 .should().dependOnClassesThat().resideInAPackage(ADAPTER_OUT),
         noClasses().that().resideInAPackage(ADAPTER_OUT)
                 .should().dependOnClassesThat().resideInAPackage(ADAPTER_IN),
         noClasses().that().resideInAnyPackage(ADAPTER_IN, ADAPTER_ROOT)
                 .should().dependOnClassesThat().resideInAPackage(PORT_OUT),
         slices().matching('com.bbh.itss.beadle.adapter.out.(*)..').should().notDependOnEachOther()
                 .ignoreDependency(alwaysTrue(), resideInAPackage(HTTP))
                 .because('the out adapters of different systems share only the HTTP client'),
         classes().that(describedAs('implement an out port') { JavaClass type ->
             !type.isInterface() && type.allRawInterfaces.any { it.packageName.contains('.port.out') }
         }).should().resideInAPackage(ADAPTER_OUT),
         classes().that().areMetaAnnotatedWith(Configuration).and().doNotHaveFullyQualifiedName(BeadleApplication.name)
                 .should().resideInAPackage(CONFIG),
         noClasses().that().resideOutsideOfPackage(PERSISTENCE)
                 .should().dependOnClassesThat().resideInAnyPackage('jakarta.persistence..', 'org.springframework.data..'),
         methods().that().areAnnotatedWith(Scheduled).should().beDeclaredInClassesThat().resideInAPackage(ADAPTER_IN)
                 .because('a schedule calls the use cases from outside, as a request does').allowEmptyShould(true),
         classes().that(describedAs('are controllers or controller advice') { JavaClass type ->
             [RestController, RestControllerAdvice, Controller].any { type.isAnnotatedWith(it) }
         }).should().resideInAPackage(WEB),
         methods().that().areAnnotatedWith(PostMapping).or().areAnnotatedWith(PutMapping)
                 .or().areAnnotatedWith(PatchMapping).or().areAnnotatedWith(DeleteMapping)
                 .should().beAnnotatedWith(AuditedRequest)
                 .because('a refused change must leave its FAILURE row in the audit trail'),
         classes().that(describedAs('are persistence types') { JavaClass type ->
             [Entity, Embeddable, MappedSuperclass, Converter].any { type.isAnnotatedWith(it) } ||
                     type.isAssignableTo(Repository)
         }).should().resideInAPackage(PERSISTENCE),
         noClasses().that().resideOutsideOfPackages(HTTP_ADAPTERS)
                 .should().dependOnClassesThat()
                 .resideInAnyPackage('org.springframework.web.client..', 'java.net.http..'),
         slices().matching('com.bbh.itss.beadle.(*)..').should().beFreeOfCycles(),
         slices().matching('com.bbh.itss.beadle.domain.(*)..').should().beFreeOfCycles(),
         slices().matching('com.bbh.itss.beadle.application.(**)').should().beFreeOfCycles(),
         slices().matching('com.bbh.itss.beadle.adapter.(**)').should().beFreeOfCycles(),
         classes().that().areAnnotatedWith(UseCase)
                 .should().resideInAPackage(APPLICATION).andShould(implementAnInPort()),
         classes().should(notDependOnOtherUseCases())]
    }

    private static DescribedPredicate<JavaClass> describedAs(String description, Closure<Boolean> test) {
        describe(description, { JavaClass type -> test(type) } as Predicate<JavaClass>)
    }

    private static ArchCondition<JavaClass> implementAnInPort() {
        new ArchCondition<JavaClass>('implement an interface of a port.in package') {
            @Override
            void check(JavaClass useCase, ConditionEvents events) {
                boolean implementsPort = useCase.allRawInterfaces.any { it.packageName.contains('.port.in') }
                events.add(new SimpleConditionEvent(useCase, implementsPort,
                        "${useCase.name} ${implementsPort ? 'implements' : 'does not implement'} an in port"))
            }
        }
    }

    private static ArchCondition<JavaClass> notDependOnOtherUseCases() {
        new ArchCondition<JavaClass>('not depend on use case classes other than themselves') {
            @Override
            void check(JavaClass type, ConditionEvents events) {
                type.directDependenciesFromSelf
                        .findAll { it.targetClass != type && it.targetClass.isAnnotatedWith(UseCase) }
                        .each { events.add(violated(it, it.description)) }
            }
        }
    }
}
