package com.bbh.itss.dso.portal.regression

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

import static com.bbh.itss.dso.portal.support.ApiJson.product

class ProTechApplyRegressionSpec extends ChangeRegressionSpecification {

    @DynamicPropertySource
    static void applyLater(DynamicPropertyRegistry registry) {
        registry.add('dso.demo.protech-apply-delay') { 'PT1S' }
    }

    def "an update ProTech applies a moment later is pending first and applied when the change is read again"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Later ${uniqueCode()}")))
        def added = [shortDescription: 'Check the audit trail', description: 'Open it.']

        when:
        def updated = api.put("/api/changes/$raised.id", editOf(raised, [shortDescription: 'Renamed release',
                                                                         tasks: raised.tasks + added])).json
        def opened = readOnceApplied(raised.id as long)

        then:
        updated.update.status == 'PENDING'
        updated.update.fields == ['shortDescription', 'tasks']
        updated.shortDescription == 'Renamed release'
        updated.tasks*.number == raised.tasks*.number + [null]
        opened.update.status == 'APPLIED'
        opened.update.fields == []
        opened.shortDescription == 'Renamed release'
        opened.tasks*.number.take(2) == raised.tasks*.number
        opened.tasks[2].number ==~ /CTASK\d{7}/
        opened.tasks[2].subMap('shortDescription', 'description', 'state') == added + [state: 'OPEN']
        opened.version > updated.version
        opened.editedVersion == updated.editedVersion
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PRODUCTION_CHANGE_TASK WHERE CHANGE_ID = ?', Integer,
                raised.id) == 3
    }

    private Map readOnceApplied(long id) {
        for (int attempt = 0; attempt < 40; attempt++) {
            Map read = api.get("/api/changes/$id").json as Map
            if (read.update.status != 'PENDING') {
                return read
            }
            sleep(250)
        }
        throw new AssertionError("ProTech has not applied the update of change $id")
    }
}
