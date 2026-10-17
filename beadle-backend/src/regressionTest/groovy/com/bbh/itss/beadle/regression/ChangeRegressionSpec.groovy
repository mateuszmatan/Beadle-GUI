package com.bbh.itss.beadle.regression

import java.time.Instant

import static com.bbh.itss.beadle.support.ChangeFixtures.changeTaskJson
import static com.bbh.itss.beadle.support.ChangeFixtures.scheduleJson
import static com.bbh.itss.beadle.support.ChangeFixtures.secureCodingJson
import static com.bbh.itss.beadle.support.ChangeFixtures.tasksJson
import static com.bbh.itss.beadle.support.ChangeFixtures.templateJson
import static com.bbh.itss.beadle.support.StandInServices.RELEASE_APPROVERS
import static com.bbh.itss.beadle.support.StandInServices.TASK_APPROVERS
import static java.time.temporal.ChronoUnit.DAYS
import static java.time.temporal.ChronoUnit.MINUTES

class ChangeRegressionSpec extends ChangeRegressionSpecification {

    def "a product's change template is saved at the version it read and a stale save is refused 409"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Ledger ${uniqueCode()}", ownerTeam: 'Ledger Ops'))
        def path = "/api/products/$created.id/change-profile"

        when:
        def suggested = api.get(path)
        def saved = api.put(path, [version: null, template: templateJson(), tasks: tasksJson()])
        def changed = api.put(path, [version: 0, template: templateJson('approvers.l1Manager': ' Emma Brooks ',
                jiraProjectKey: 'ledg'), tasks: [[assignmentGroup: ' Service Desk ', shortDescription: ' Tell the users ',
                                                   description: ' Post the notice. ']]])
        def stale = api.put(path, [version: 0, template: templateJson(), tasks: tasksJson()])

        then:
        suggested.status == 200
        suggested.json.version == null
        suggested.json.template.assignmentGroup == 'Ledger Ops'
        saved.status == 200
        saved.json.version == 0
        saved.json.template == templateJson()
        changed.status == 200
        changed.json.version == 1
        changed.json.template == templateJson('approvers.l1Manager': 'Emma Brooks', jiraProjectKey: 'LEDG')
        changed.json.tasks*.subMap('assignmentGroup', 'shortDescription', 'description') ==
                [[assignmentGroup: 'Service Desk', shortDescription: 'Tell the users', description: 'Post the notice.']]
        stale.status == 409
        api.get(path).json == changed.json
        api.get('/api/change-profiles').json.find { it.productId == created.id } ==
                [productId: created.id, productName: created.name, version: 1, updatedAt: changed.json.updatedAt]
    }

    def "a change is previewed, raised from a FixVersion's epics, gets its change tasks in ProTech and is listed for its department"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Payments ${uniqueCode()}"))
        def key = 'PAY' + created.id
        api.put("/api/products/$created.id/change-profile", [template: templateJson(jiraProjectKey: key),
                                                             tasks: tasksJson()])

        when:
        def versions = api.get("/api/products/$created.id/jira/versions").json
        def fixVersion = versions[0].name
        def epics = api.get("/api/products/$created.id/jira/epics?fixVersion=${enc(fixVersion)}").json
        def stories = api.get("/api/products/$created.id/jira/stories?fixVersion=${enc(fixVersion)}" +
                "&epics=${epics*.key.join(',')}").json
        def request = change(created, fixVersion, epics*.key, stories*.key, [template: templateJson(jiraProjectKey: key)])
        def preview = api.post('/api/changes/preview', request)

        then:
        versions*.name == ["$key 2.1", "$key 2.0"]*.toString()
        epics*.key == ["$key-11", "$key-12"]*.toString()
        stories*.epicKey == epics*.key
        preview.status == 200
        [preview.json.id, preview.json.number, preview.json.version] == [null, null, null]
        preview.json.subMap('state', 'departmentId', 'departmentName', 'openedBy', 'fixVersion', 'tasks') ==
                [state: 'DRAFT', departmentId: 3, departmentName: 'Corporate Technology', openedBy: SIGNED_IN,
                 fixVersion: fixVersion, tasks: []]
        preview.json.shortDescription == "$created.name $fixVersion: ${epics*.summary.join('; ')}"
        stories.every { preview.json.description.contains("- $it.key $it.summary") }

        when:
        def raised = api.post('/api/changes', request)
        def tasked = createTasks(raised.json as Map, [
                changeTaskJson(assignmentGroup: 'Release Management', application: 'Payments',
                        packages: 'payments-4.2.jar'),
                changeTaskJson(assignmentGroup: 'Service Desk', shortDescription: 'Tell the users')])

        then:
        raised.status == 201
        raised.header('Location') == "$api.baseUrl/api/changes/${raised.json.id}"
        raised.json.number ==~ /CHG\d{7}/
        raised.json.url == "https://protech.bbh.com/$raised.json.number"
        raised.json.shortDescription == preview.json.shortDescription
        raised.json.epicKeys == epics*.key
        raised.json.storyKeys == stories*.key
        raised.json.workflow == [[state: 'DRAFT', enteredAt: raised.json.createdAt]]
        tasked.tasks*.number.every { it ==~ /CTASK\d{7}/ }
        tasked.tasks*.subMap('start', 'approval', 'approvers', 'state') ==
                [[start: START.plus(1, MINUTES).toString(), approval: 'NOT_APPROVED', approvers: RELEASE_APPROVERS,
                  state: 'OPEN'],
                 [start: null, approval: 'NOT_APPROVED', approvers: TASK_APPROVERS, state: 'OPEN']]
        tasked.tasks[0].details.subMap('platform', 'application', 'configurationItem') ==
                [platform: 'None', application: 'Payments', configurationItem: 'CertScanner']
        SERVICES.proTechChange(raised.json.number as String).tasks*.number == tasked.tasks*.number

        expect:
        api.get("/api/changes/$raised.json.id").json.findAll { it.key != 'syncedAt' } ==
                tasked.findAll { it.key != 'syncedAt' }
        api.get('/api/changes?departmentId=3').json.find { it.id == raised.json.id }.tasks == tasked.tasks
        !(raised.json.id in api.get('/api/changes?departmentId=4').json*.id)
    }

    def "a secure coding ticket is created in CyberTrack through Beadle-Internal and shown on the change"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Secure ${uniqueCode()}")))
        def request = [version: raised.version, departmentId: raised.departmentId, implementationDate: '10202026'] +
                secureCodingJson()

        when:
        def ticketed = api.post("/api/changes/$raised.id/secure-coding", request)
        def sent = SERVICES.requests.findAll { it.path == '/api/internal/cybertrack/tickets' }.last()

        then:
        ticketed.status == 200
        def key = ticketed.json.template.secureCodingTicket as String
        key ==~ /SCP-\d+/
        ticketed.json.template.secureCoding == secureCodingJson()
        sent.json == [changeNumber: raised.number, applicationName: raised.productName,
                      implementationDate: '2026-10-20', inputs: secureCodingJson()]
        SERVICES.proTechChange(raised.number as String).template.secureCodingTicket == key
        api.get("/api/changes/$raised.id").json.template.secureCodingTicket == key

        when:
        def second = api.post("/api/changes/$raised.id/secure-coding", request + [version: ticketed.json.version])

        then:
        second.status == 409
        second.json.detail == "$raised.number already has the secure coding ticket $key"
    }

    def "its department updates an open change and ProTech applies it"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Update ${uniqueCode()}")))
        def later = scheduleJson(START.plus(1, DAYS))
        def edit = editOf(raised, [shortDescription: ' Renamed release ', schedule: later,
                                   template: raised.template + [usersAffected: 'Ledger operators'],
                                   tasks: [raised.tasks[0] + [details: raised.tasks[0].details +
                                                                       [shortDescription: 'Deploy it all']],
                                           changeTaskJson(assignmentGroup: 'Service Desk',
                                                   shortDescription: 'Check the audit trail')]])

        when:
        def updated = api.put("/api/changes/$raised.id", edit)

        then:
        updated.status == 200
        updated.json.shortDescription == 'Renamed release'
        updated.json.schedule == later
        updated.json.template.usersAffected == 'Ledger operators'
        updated.json.tasks*.details*.shortDescription == ['Deploy it all', 'Check the audit trail',
                                                          raised.tasks[1].details.shortDescription]
        updated.json.tasks*.state == ['OPEN', 'OPEN', 'CANCELED']
        updated.json.tasks*.number == [raised.tasks[0].number, updated.json.tasks[1].number, raised.tasks[1].number]
        updated.json.update.subMap('status', 'departmentName', 'fields') ==
                [status: 'APPLIED', departmentName: 'Corporate Technology', fields: []]
        SERVICES.proTechChange(raised.number as String).shortDescription == 'Renamed release'

        expect:
        api.get("/api/changes/$raised.id").json.findAll { it.key != 'syncedAt' } ==
                updated.json.findAll { it.key != 'syncedAt' }
        api.put("/api/changes/$raised.id", edit).status == 409
        api.put("/api/changes/$raised.id", editOf(updated.json as Map, [departmentId: 4])).status == 403
    }

    def "the approvers who have not approved yet are reminded"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Remind ${uniqueCode()}")),
                [changeTaskJson(assignmentGroup: 'Release Management'), changeTaskJson(assignmentGroup: 'Service Desk')])
        SERVICES.proTechChange(raised.number as String).approvals[0].state = 'APPROVED'

        when:
        def everyone = api.post("/api/changes/$raised.id/reminders", [departmentId: 3])
        def one = api.post("/api/changes/$raised.id/reminders", [departmentId: 3, task: raised.tasks[1].number])

        then:
        everyone.status == 200
        everyone.json.approvals*.reminder*.sentTo == [null, ['Olivia Bennett'], ['James Carter'], ['Jane Smith']]
        everyone.json.tasks*.reminder*.sentTo == [RELEASE_APPROVERS, TASK_APPROVERS]
        one.status == 200
        Instant.parse(one.json.tasks[1].reminder.sentAt).isAfter(Instant.parse(everyone.json.tasks[1].reminder.sentAt))
        one.json.tasks[0].reminder == everyone.json.tasks[0].reminder
        api.get("/api/changes/$raised.id").json.tasks*.reminder == one.json.tasks*.reminder
        api.post("/api/changes/$raised.id/reminders", [departmentId: 3, approval: 'BUSINESS']).status == 409
    }
}
