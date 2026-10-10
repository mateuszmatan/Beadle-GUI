package com.bbh.itss.dso.portal.regression

import static com.bbh.itss.dso.portal.support.ChangeFixtures.scheduleJson
import static com.bbh.itss.dso.portal.support.ChangeFixtures.secureCodingJson
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

    def "the wizard options are the ProTech categories, change types, risk answers and change task choices"() {
        when:
        def options = api.get('/api/changes/options')

        then:
        options.status == 200
        options.json.keySet() as List == ['categories', 'types', 'risk', 'platforms', 'importances',
                                          'releaseManagement']
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
        options.json.platforms == ['None', 'Mainframe', 'Distributed', 'OpenShift', 'Cognos/Motio']
        options.json.importances == ['1 - Critical', '2 - High', '3 - Moderate', '4 - Low', '5 - Planning']
        options.json.releaseManagement == 'Release Management'
        api.get("/api/lookups/assignment-groups?q=${enc(options.json.releaseManagement)}").json*.value ==
                [options.json.releaseManagement]
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
        def department = api.post('/api/departments', [name: "Treasury Desk ${uniqueCode()}"]).json
        def renamed = api.put("/api/departments/$department.id", [name: "Treasury Office ${uniqueCode()}",
                                                                  version: department.version]).json

        expect:
        api.get('/api/lookups/users?q=GRACE').json*.value == ['Grace Mitchell', 'Grace Turner']
        api.get("/api/lookups/users?q=${enc('mateusz.matan@')}").json ==
                [[value: SIGNED_IN, detail: 'mateusz.matan@bbh.com']]
        api.get("/api/lookups/departments?q=${enc(renamed.name)}").json*.value == [renamed.name]
        api.get("/api/lookups/departments?q=${enc(department.name)}").json == []
        api.get('/api/lookups/departments?q=compliance').json == [[value: 'Compliance', detail: 'Cost centre 4630']]
        api.get('/api/lookups/departments?q=corporate%20technology').json ==
                [[value: 'Corporate Technology', detail: 'Cost centre 4310']]
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

    def "a template keeps the wizard fields, computes its risk whatever risk it is sent and answers what it is not sent with the first option"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Wizard ${uniqueCode()}"))
        def template = templateJson(requestedFor: ' Ann Lee ', requestedBy: 'Jane Smith', department: 'Custody',
                assignedTo: 'Grace Turner', directBusinessService: 'Custody Platform', usersAffected: 'Custody users',
                secureCodingTicket: ' SCP-1234 ', type: 'BUSINESS_CRITICAL', category: 'Database', risk: 'Low',
                'riskAssessment.backoutTesting': 'Unable to test', 'secureCoding.apoNumber': ' APO-12345 ',
                'secureCoding.bitbucketUrl': 'https://bitbucket.bbh.com/projects/WIZ/repos/wiz')

        when:
        def saved = api.put("/api/products/$created.id/change-profile", [template: template, tasks: tasksJson()])
        def unassessed = api.put("/api/products/$created.id/change-profile", [version: 0, tasks: tasksJson(),
                template: templateJson(riskAssessment: [:], risk: 'High')])

        then:
        saved.status == 200
        saved.json.template == template + [requestedFor: 'Ann Lee', secureCodingTicket: 'SCP-1234', risk: 'High',
                                           secureCoding: template.secureCoding + [apoNumber: 'APO-12345']]
        unassessed.json.template.risk == 'Low'
        unassessed.json.template.riskAssessment == [bbhWorkgroups: 'Single', changeComplexity: 'Simple',
                bbhUsers: 'Less than 5', validationComplexity: 'Simple', bbhApplications: 'Single',
                backoutTesting: 'Less than 30 minutes', clientsOutsideBbh: 'No clients', platformStatus: 'Existing',
                businessImpact: 'None']
    }

    def "a change with downtime is raised with its window, the signed-in user and the wizard fields"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Downtime ${uniqueCode()}", departmentId: 4))
        def key = 'DT' + created.id
        def fixVersion = api.get("/api/products/$created.id/jira/versions?project=$key").json[0].name
        def epic = api.get("/api/products/$created.id/jira/epics?fixVersion=${enc(fixVersion)}&project=$key")
                .json[0].key
        def template = templateJson(jiraProjectKey: key, downtime: true, requestedBy: 'Olivia Bennett',
                usersAffected: 'Custody operators during the window', secureCodingTicket: 'SCP-4321',
                secureCoding: secureCodingJson(), 'riskAssessment.bbhUsers': 'All users')
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
        raised.json.template.secureCodingTicket == null
        raised.json.template.secureCoding == secureCodingJson()
        !raised.json.description.contains('Secure coding ticket')
        raised.json.tasks == []

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
        updated.json.tasks == []
        updated.json.openedBy == SIGNED_IN
        api.get("/api/changes/$raised.json.id").json.template.usersAffected == 'Nobody'
    }

    def "a secure coding ticket is created in CyberTrack for a raised change, sent to ProTech and shown on the change"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Secure ${uniqueCode()}")), [])
        def inputs = secureCodingJson() + [apoNumber: ' APO-24680 ']

        when:
        def incomplete = api.post("/api/changes/$raised.id/secure-coding", ticketOf(raised, [apoNumber: ' ',
                implementationDate: '2026-10-15', bitbucketUrl: null, qcApplicationLink: 'cert.qc.bbh.com']))
        def foreign = api.post("/api/changes/$raised.id/secure-coding", ticketOf(raised, [departmentId: 4]))
        def unversioned = api.post("/api/changes/$raised.id/secure-coding", ticketOf(raised, [version: null]))

        then:
        incomplete.status == 400
        incomplete.json.errors.collect { [it.field, it.message] } == [['apoNumber', 'is required'],
                ['bitbucketUrl', 'is required'], ['qcApplicationLink', 'must be a link starting with https:// or http://'],
                ['implementationDate', 'must be a date written MMDDYYYY, such as 10152026']]
        foreign.status == 403
        unversioned.status == 400
        api.post('/api/changes/99999/secure-coding', ticketOf(raised, [:])).status == 404

        when:
        def ticketed = api.post("/api/changes/$raised.id/secure-coding", ticketOf(raised, inputs))

        then:
        ticketed.status == 200
        ticketed.json.template.secureCodingTicket ==~ /SCP-\d{4}/
        ticketed.json.template.secureCoding == secureCodingJson(apoNumber: 'APO-24680')
        ticketed.json.template.findAll { !(it.key in ['secureCodingTicket', 'secureCoding']) } ==
                raised.template.findAll { !(it.key in ['secureCodingTicket', 'secureCoding']) }
        ticketed.json.update.status in ['PENDING', 'APPLIED']
        api.get("/api/changes/$raised.id").json.template.secureCodingTicket == ticketed.json.template.secureCodingTicket

        when:
        def again = api.post("/api/changes/$raised.id/secure-coding", ticketOf(ticketed.json as Map, inputs))

        then:
        again.status == 409
    }

    private static Map ticketOf(Map change, Map edits) {
        [version: change.version, departmentId: change.departmentId, implementationDate: '10152026'] +
                secureCodingJson() + edits
    }
}
