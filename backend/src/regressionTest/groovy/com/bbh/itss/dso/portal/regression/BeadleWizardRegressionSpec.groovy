package com.bbh.itss.dso.portal.regression

import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ChangeFixtures.scheduleJson
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasksJson
import static com.bbh.itss.dso.portal.support.ChangeFixtures.templateJson

class BeadleWizardRegressionSpec extends ChangeRegressionSpecification {

    static final List<String> KINDS = ['users', 'departments', 'assignment-groups', 'releases', 'configuration-items',
                                       'incidents', 'problems', 'clients']

    def "the portal names the signed-in user until single sign-on comes"() {
        when:
        def me = api.get('/api/me')

        then:
        me.status == 200
        me.json == [name: SIGNED_IN]
    }

    def "the wizard options are the ProTech categories, change types and risk answers"() {
        when:
        def options = api.get('/api/changes/options')

        then:
        options.status == 200
        options.json.keySet() as List == ['categories', 'types', 'risk']
        options.json.categories == ['Application', 'Hardware', 'Infrastructure', 'System Software', 'Network',
                                    'Telecom', 'Data Amendment', 'Desktop Software', 'Storage', 'Facilities', 'Other',
                                    'Database']
        options.json.types == [[value: 'STANDARD', label: 'Standard'], [value: 'EMERGENCY', label: 'Emergency'],
                               [value: 'BUSINESS_CRITICAL', label: 'Business Critical'], [value: 'MODEL', label: 'Model']]
        options.json.risk == [bbhWorkgroups       : ['Single', '2-3', 'More than 3'],
                              changeComplexity    : ['Simple', 'Moderate', 'Very'],
                              bbhUsers            : ['Less than 5', '5-25', '26-250', 'All users'],
                              validationComplexity: ['Simple', 'Moderate', 'Very'],
                              bbhApplications     : ['Single', 'Two', 'More than 2'],
                              backoutTesting      : ['Less than 30 minutes', '30 mins - 2 hours',
                                                     'Greater than 2 hours', 'Unable to test'],
                              clientsOutsideBbh   : ['No clients', 'Single', 'More than one but not all',
                                                     'All clients'],
                              platformStatus      : ['Existing', 'New', 'Decommissioned'],
                              businessImpact      : ['None', 'Low', 'Medium', 'High']]
    }

    def "every lookup answers at most 20 values with their detail"() {
        given:
        createProduct(product(code: uniqueCode(), name: "Listed ${uniqueCode()}"))

        expect:
        KINDS.every { kind ->
            def found = api.get("/api/lookups/$kind")
            assert found.status == 200: found
            assert found.json.size() in 1..20
            assert found.json.every { it.keySet() as List == ['value', 'detail'] && it.value }
            true
        }
        api.get('/api/lookups/users').json.size() == 20
    }

    def "a lookup finds the text in the value or the detail, ignoring case"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Lookup ${uniqueCode()}",
                ownerTeam: 'Treasury Engineering'))

        expect:
        api.get('/api/lookups/users?q=GRACE').json*.value == ['Grace Mitchell', 'Grace Turner']
        api.get("/api/lookups/users?q=${enc('mateusz.matan@')}").json ==
                [[value: SIGNED_IN, detail: 'mateusz.matan@bbh.com']]
        api.get('/api/lookups/departments').json*.value.containsAll(['AI Lab', 'Capital Partners',
                                                                    'Corporate Technology', 'Custody', 'Fund Services'])
        api.get("/api/lookups/configuration-items?q=${enc(created.name)}").json ==
                [[value: created.name, detail: 'Treasury Engineering']]
        api.get("/api/lookups/assignment-groups?q=${enc('treasury engineering')}").json*.value ==
                ['Treasury Engineering Application Support']
        api.get("/api/lookups/releases?q=${enc(created.name)}").json.every {
            it.value.startsWith(created.name + ' ') && it.detail == created.code
        }
        api.get('/api/lookups/incidents?q=nav').json*.value == ['INC0104377']
        api.get('/api/lookups/problems?q=PRB0040319').json*.value == ['PRB0040319']
        api.get('/api/lookups/clients?q=nobody%20at%20all').json == []
    }

    def "an unknown lookup is not found"() {
        when:
        def missing = api.get('/api/lookups/owners?q=a')

        then:
        missing.status == 404
        missing.json.detail == 'Lookup owners does not exist'
    }

    def "a template keeps the wizard fields and computes its risk, whatever risk it is sent"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Wizard ${uniqueCode()}"))
        def template = templateJson(requestedFor: ' Ann Lee ', requestedBy: 'Jane Smith', department: 'Custody',
                assignedTo: 'Grace Turner', directBusinessService: 'Custody Platform', usersAffected: 'Custody users',
                secureCodingTicket: ' APPSEC-1234 ', type: 'BUSINESS_CRITICAL', category: 'Database', risk: 'Low',
                'riskAssessment.backoutTesting': 'Unable to test')

        when:
        def saved = api.put("/api/products/$created.id/change-profile", [template: template, tasks: tasksJson()])
        def unassessed = api.put("/api/products/$created.id/change-profile", [version: 0, tasks: tasksJson(),
                template: templateJson(riskAssessment: [:], risk: 'High')])

        then:
        saved.status == 200
        saved.json.template == template + [requestedFor: 'Ann Lee', secureCodingTicket: 'APPSEC-1234', risk: 'High']
        unassessed.json.template.risk == null
        unassessed.json.template.riskAssessment.values().every { it == null }
    }

    def "a change with downtime is raised with its window, the signed-in user and the wizard fields"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Downtime ${uniqueCode()}", departmentId: 4))
        def key = 'DT' + created.id
        def fixVersion = api.get("/api/products/$created.id/jira/versions?project=$key").json[0].name
        def epic = api.get("/api/products/$created.id/jira/epics?fixVersion=${enc(fixVersion)}&project=$key")
                .json[0].key
        def template = templateJson(jiraProjectKey: key, downtime: true, requestedBy: 'Olivia Bennett',
                usersAffected: 'Custody operators during the window', secureCodingTicket: 'APPSEC-4321',
                'riskAssessment.bbhUsers': 'All users')
        def schedule = scheduleJson(START, true)

        when:
        def raised = api.post('/api/changes', change(created, fixVersion, [epic], [],
                [template: template, schedule: schedule]))

        then:
        raised.status == 201
        raised.json.openedBy == SIGNED_IN
        raised.json.departmentName == 'Custody'
        raised.json.schedule == schedule
        raised.json.template == raisedAs(template, fixVersion, 'Custody') + [risk: 'High']
        raised.json.template.requestedBy == 'Olivia Bennett'
        raised.json.template.requestedFor == SIGNED_IN
        raised.json.description.contains(". Downtime ${schedule.downtimeStart.replace('T', ' ').take(16)} to ")
        raised.json.description.contains('\n\nRisk: High\n')
        raised.json.description.contains('Number of BBH users impacted: All users')
        raised.json.description.contains('\n\nUsers affected:\nCustody operators during the window')
        raised.json.description.contains('\n\nSecure coding ticket: APPSEC-4321')

        when:
        def moved = scheduleJson(START, true) + [downtimeEnd: START.plusSeconds(3600).toString()]
        def updated = api.put("/api/changes/$raised.json.id", editOf(raised.json, [schedule: moved,
                template: raised.json.template + [usersAffected: 'Nobody', assignedTo: 'Ann Lee']]))

        then:
        updated.status == 200
        updated.json.schedule == moved
        updated.json.template.usersAffected == 'Nobody'
        updated.json.template.assignedTo == 'Ann Lee'
        updated.json.update.subMap('status', 'fields') == [status: 'APPLIED', fields: []]
        updated.json.openedBy == SIGNED_IN
        api.get("/api/changes/$raised.json.id").json.template.usersAffected == 'Nobody'
    }
}
