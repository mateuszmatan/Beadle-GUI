package com.bbh.itss.beadle.regression

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

import static com.bbh.itss.beadle.support.ChangeFixtures.changeTaskJson

class ProTechApplyRegressionSpec extends ChangeRegressionSpecification {

    @DynamicPropertySource
    static void applyLater(DynamicPropertyRegistry registry) {
        registry.add('beadle.demo.protech-apply-delay') { 'PT1S' }
    }

    def "an update ProTech applies a moment later is pending first and applied when the change is read again"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Later ${uniqueCode()}")))
        def added = changeTaskJson(assignmentGroup: 'Service Desk', shortDescription: 'Check the audit trail',
                description: 'Open it.')

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
        opened.tasks[2].details.subMap('assignmentGroup', 'shortDescription', 'description', 'configurationItem',
                'importance') == added.details + [configurationItem: raised.template.configurationItem,
                                                  importance: '3 - Moderate']
        opened.tasks[2].subMap('start', 'approval', 'approvers', 'state') ==
                [start: null, approval: 'NOT_APPROVED', approvers: ['Jane Smith'], state: 'OPEN']
        opened.version > updated.version
        opened.editedVersion == updated.editedVersion
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PRODUCTION_CHANGE_TASK WHERE CHANGE_ID = ?', Integer,
                raised.id) == 3
    }

    def "listing the changes stores the update ProTech applied meanwhile and records it as synced by the user"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Listed ${uniqueCode()}")))
        def updated = api.put("/api/changes/$raised.id", editOf(raised, [shortDescription: 'Listed release'])).json

        when:
        Map listed = listOnceApplied(raised.id as long, raised.departmentId as long)

        then:
        updated.update.status == 'PENDING'
        listed.update.status == 'APPLIED'
        jdbc.queryForList('''SELECT USER_NAME, OBJECT_TYPE, OBJECT_ID, OUTCOME, DETAILS FROM BEADLE_ACTION_HISTORY
                WHERE OBJECT_KEY = ? AND ACTION = 'CHANGE_SYNCED' ORDER BY ID''', raised.number).any { row ->
            row.USER_NAME == SIGNED_IN && row.OBJECT_TYPE == 'CHANGE' && row.OBJECT_ID as long == raised.id &&
                    row.OUTCOME == 'SUCCESS' && row.DETAILS == "{\"departmentId\":$raised.departmentId}".toString()
        }
    }

    private Map listOnceApplied(long id, long departmentId) {
        for (int attempt = 0; attempt < 40; attempt++) {
            Map listed = (api.get("/api/changes?departmentId=$departmentId").json as List<Map>).find { it.id == id }
            if (listed.update.status != 'PENDING') {
                return listed
            }
            sleep(250)
        }
        throw new AssertionError("ProTech has not applied the update of change $id")
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
