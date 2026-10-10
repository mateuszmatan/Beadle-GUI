package com.bbh.itss.dso.portal.regression

import java.time.Instant
import java.time.LocalDateTime

import static com.bbh.itss.dso.portal.domain.change.TaskDetails.suggestedTasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.changeTaskJson
import static com.bbh.itss.dso.portal.support.ChangeFixtures.changeTasksJson
import static com.bbh.itss.dso.portal.support.ChangeFixtures.detailsJson
import static com.bbh.itss.dso.portal.support.ChangeFixtures.scheduleJson
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasksJson
import static com.bbh.itss.dso.portal.support.ChangeFixtures.templateJson
import static java.time.ZoneOffset.UTC
import static java.time.temporal.ChronoUnit.DAYS
import static java.time.temporal.ChronoUnit.HOURS
import static java.time.temporal.ChronoUnit.MINUTES

class ProductionChangeRegressionSpec extends ChangeRegressionSpecification {

    static final List<String> CHANGE_KEYS = ['id', 'number', 'productId', 'productCode', 'productName',
                                             'departmentId', 'departmentName', 'openedBy', 'fixVersion', 'schedule',
                                             'shortDescription', 'description', 'template', 'epicKeys', 'storyKeys',
                                             'tasks', 'url', 'state', 'workflow', 'syncedAt', 'syncProblem',
                                             'update', 'version', 'editedVersion', 'createdAt']
    static final List<String> TEMPLATE_KEYS = ['jiraProjectKey', 'requestedFor', 'requestedBy', 'department',
                                               'assignmentGroup', 'category', 'assignedTo', 'type', 'release',
                                               'configurationItem', 'incident', 'directBusinessService', 'problem',
                                               'risk', 'affectedClients', 'usersAffected', 'approvers',
                                               'downtime', 'timing', 'planning', 'privilegedAccess', 'riskAssessment',
                                               'secureCodingTicket']
    static final List<String> SCHEDULE_PATHS = ['schedule.installationStart', 'schedule.installationEnd',
                                                'schedule.validationStart', 'schedule.validationEnd',
                                                'schedule.firstUsage']
    static final List<String> TASK_KEYS = ['number', 'details', 'start', 'approval', 'state']
    static final List<String> TASK_DETAIL_KEYS = ['assignmentGroup', 'assignedTo', 'configurationItem', 'platform',
                                                  'application', 'packages', 'backoutPackages', 'importance',
                                                  'shortDescription', 'description', 'additionalComments']
    static final String MODERATE = '3 - Moderate'
    static final String NOT_YET_REQUESTED = 'Not Yet Requested'

    def "a product first gets a suggested template, then saves and changes its own at the version it read"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Ledger ${uniqueCode()}", ownerTeam: 'Ledger Ops',
                description: 'Posts the ledger.'))
        def privileged = templateJson('privilegedAccess.required': true,
                'privilegedAccess.users': [[user: ' Jane Smith ', account: 'adm_jsmith'], [user: 'Ann Lee', account: 'adm_alee']],
                'approvers.l1Manager': ' Emma Brooks ', jiraProjectKey: 'ledg', incident: ' INC0012345 ')

        when:
        def suggested = api.get("/api/products/$created.id/change-profile")

        then:
        suggested.status == 200
        suggested.json.keySet() as List == ['productId', 'productName', 'version', 'updatedAt', 'template', 'tasks']
        [suggested.json.productId, suggested.json.productName, suggested.json.version] == [created.id, created.name, null]
        suggested.json.template.keySet() as List == TEMPLATE_KEYS
        suggested.json.template.subMap('requestedFor', 'department', 'configurationItem', 'assignmentGroup',
                'category', 'type', 'release', 'risk', 'approvers', 'downtime', 'timing',
                'privilegedAccess', 'riskAssessment', 'secureCodingTicket') ==
                [requestedFor: null, department: null, configurationItem: created.name, assignmentGroup: 'Ledger Ops',
                 category: 'Application', type: 'STANDARD', release: null, risk: 'Low',
                 approvers: [l1Manager: null, l2Manager: null, businessApprover: null],
                 downtime: false, timing: [installationStart: '18:00', installationHours: 2, validationHours: 1],
                 privilegedAccess: [required: false, users: []],
                 riskAssessment: [bbhWorkgroups: 'Single', changeComplexity: 'Simple', bbhUsers: 'Less than 5',
                                  validationComplexity: 'Simple', bbhApplications: 'Single',
                                  backoutTesting: 'Less than 30 minutes', clientsOutsideBbh: 'No clients',
                                  platformStatus: 'Existing', businessImpact: 'None'],
                 secureCodingTicket: null]
        suggested.json.template.planning.keySet() as List ==
                ['testSummary', 'implementationPlan', 'validationPlan', 'backoutPlan', 'firstUsePlan']
        suggested.json.tasks == suggestedTasks(created.name as String, 'Ledger Ops').collect { detailsJson(it) }
        suggested.json.tasks.every { it.keySet() as List == TASK_DETAIL_KEYS }
        suggested.json.tasks*.subMap('assignmentGroup', 'platform', 'application', 'importance', 'shortDescription') ==
                [[assignmentGroup: 'Release Management', platform: 'None', application: created.name, importance: null,
                  shortDescription: "Deploy $created.name to production".toString()],
                 [assignmentGroup: 'Ledger Ops', platform: null, application: null, importance: MODERATE,
                  shortDescription: "Validate $created.name in production".toString()]]

        when:
        def saved = api.put("/api/products/$created.id/change-profile", [version: null, template: templateJson(),
                                                                         tasks: tasksJson()])
        def changed = api.put("/api/products/$created.id/change-profile", [version: 0, template: privileged,
                tasks: [[assignmentGroup: ' release management ', shortDescription: ' Deploy the ledger ',
                         description: ' Deploy it. ', platform: 'OpenShift', application: 'Ledger',
                         packages: ' ledger-2.0.jar ', importance: '1 - Critical']]])
        def stale = api.put("/api/products/$created.id/change-profile", [version: 0, template: templateJson(),
                                                                         tasks: tasksJson()])

        then:
        saved.status == 200
        saved.json.version == 0
        saved.json.template == templateJson()
        saved.json.tasks == tasks().collect { detailsJson(it) }
        saved.json.tasks*.importance == [MODERATE] * 2
        changed.json.version == 1
        changed.json.template == templateJson('privilegedAccess.required': true,
                'privilegedAccess.users': [[user: 'Jane Smith', account: 'adm_jsmith'], [user: 'Ann Lee', account: 'adm_alee']],
                'approvers.l1Manager': 'Emma Brooks', jiraProjectKey: 'LEDG', incident: 'INC0012345')
        changed.json.tasks == [[assignmentGroup: 'release management', assignedTo: null, configurationItem: null,
                                platform: 'OpenShift', application: 'OCP', packages: 'ledger-2.0.jar',
                                backoutPackages: null, importance: null, shortDescription: 'Deploy the ledger',
                                description: 'Deploy it.', additionalComments: null]]
        stale.status == 409
        api.get("/api/products/$created.id/change-profile").json == changed.json
        api.get('/api/change-profiles').json.find { it.productId == created.id } ==
                [productId: created.id, productName: created.name, version: 1, updatedAt: changed.json.updatedAt]
    }

    def "a template with #problem is refused against its field"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Product ${uniqueCode()}"))

        when:
        def response = api.put("/api/products/$created.id/change-profile", [template: templateJson(edits),
                                                                            tasks: tasks])

        then:
        response.status == 400
        response.json.errors*.field == [field]
        api.get("/api/products/$created.id/change-profile").json.version == null

        where:
        problem                          | edits                               | tasks         || field
        'a Jira key with a dash'         | [jiraProjectKey: 'ce-rt']           | tasksJson()   || 'template.jiraProjectKey'
        'no assignment group'            | [assignmentGroup: ' ']              | tasksJson()   || 'template.assignmentGroup'
        'an unknown type'                | [type: 'MINOR']                     | tasksJson()   || 'template.type'
        'no backout plan'                | ['planning.backoutPlan': ' ']       | tasksJson()   || 'template.planning.backoutPlan'
        'a long first use plan'          | ['planning.firstUsePlan': 'x' * 2001] | tasksJson() || 'template.planning.firstUsePlan'
        'no installation start'          | ['timing.installationStart': '6pm'] | tasksJson()   || 'template.timing.installationStart'
        'an installation of four days'   | ['timing.installationHours': 96]    | tasksJson()   || 'template.timing.installationHours'
        'a number of users off the list' | ['riskAssessment.bbhUsers': '10']   | tasksJson()   || 'template.riskAssessment.bbhUsers'
        'a platform status off the list' | ['riskAssessment.platformStatus': 'Existing platform'] | tasksJson() || 'template.riskAssessment.platformStatus'
        'a category off the list'        | [category: 'Software']              | tasksJson()   || 'template.category'
        'no category'                    | [category: ' ']                     | tasksJson()   || 'template.category'
        'a long requester'               | [requestedFor: 'x' * 201]           | tasksJson()   || 'template.requestedFor'
        'a long department'              | [department: 'é' * 51]              | tasksJson()   || 'template.department'
        'a long secure coding ticket'    | [secureCodingTicket: 'x' * 41]      | tasksJson()   || 'template.secureCodingTicket'
        'a privileged user without account' | ['privilegedAccess.required': true, 'privilegedAccess.users': [[user: 'A', account: 'a'], [user: 'B', account: 'b'], [user: 'C', account: ' ']]] | tasksJson() || 'template.privilegedAccess.users[2].account'
        'privileged access without users' | ['privilegedAccess.required': true] | tasksJson()  || 'template.privilegedAccess.users'
        'users without privileged access' | ['privilegedAccess.users': [[user: 'A', account: 'a']]] | tasksJson() || 'template.privilegedAccess.users'
        'eight privileged users'         | ['privilegedAccess.required': true, 'privilegedAccess.users': (1..8).collect { [user: "U$it", account: "u$it"] }] | tasksJson() || 'template.privilegedAccess.users'
        'no planning'                    | [planning: null]                    | tasksJson()   || 'template.planning'
        'no tasks'                       | [:]                                 | []            || 'tasks'
        'a task without assignment group' | [:]                                | [[shortDescription: 'Deploy', description: 'Deploy.']] || 'tasks[0].assignmentGroup'
        'a task without description'     | [:]                                 | [[assignmentGroup: 'Ops', shortDescription: 'Deploy', description: ' ']] || 'tasks[0].description'
        'a long task'                    | [:]                                 | [[assignmentGroup: 'Ops', shortDescription: 'é' * 81, description: 'Deploy.']] || 'tasks[0].shortDescription'
        'an importance off the list'     | [:]                                 | tasksJson() + [[assignmentGroup: 'Ops', shortDescription: 'Deploy', description: 'Deploy.', importance: 'Urgent']] || 'tasks[2].importance'
        'a platform off the list'        | [:]                                 | [[assignmentGroup: 'Release Management', shortDescription: 'Deploy', description: 'Deploy.', platform: 'Windows']] || 'tasks[0].platform'
        'fifty-one tasks'                | [:]                                 | tasksJson(51) || 'tasks'
    }

    def "the stored change profiles are listed by product name, without products that have none"() {
        given:
        def zeta = createProduct(product(code: uniqueCode(), name: "Zeta ${uniqueCode()}"))
        def alpha = createProduct(product(code: uniqueCode(), name: "Alpha ${uniqueCode()}"))
        def none = createProduct(product(code: uniqueCode(), name: "Beta ${uniqueCode()}"))
        api.put("/api/products/$zeta.id/change-profile", [template: templateJson(), tasks: tasksJson()])
        api.put("/api/products/$alpha.id/change-profile", [template: templateJson(), tasks: tasksJson()])

        when:
        def listed = api.get('/api/change-profiles')

        then:
        listed.status == 200
        listed.json*.productName == listed.json*.productName.sort(false)
        listed.json.findAll { it.productId in [zeta.id, alpha.id, none.id] }*.productName == [alpha.name, zeta.name]
        listed.json.every { it.keySet() as List == ['productId', 'productName', 'version', 'updatedAt'] }
    }

    def "Jira FixVersions, epics and stories come from the stored, the suggested or the given project"() {
        given:
        def created = createProduct(product(code: 'JV' + uniqueCode('X'), name: "Jira ${uniqueCode()}"))
        def suggestedKey = created.code.take(4)

        when:
        def suggested = api.get("/api/products/$created.id/jira/versions")
        api.put("/api/products/$created.id/change-profile", [template: templateJson(jiraProjectKey: 'STORED'),
                                                             tasks: tasksJson()])
        def stored = api.get("/api/products/$created.id/jira/versions")
        def given = api.get("/api/products/$created.id/jira/versions?project=other")
        def version = given.json[0].name
        def epics = api.get("/api/products/$created.id/jira/epics?fixVersion=${enc(version)}&project=OTHER").json
        def stories = api.get("/api/products/$created.id/jira/stories?fixVersion=${enc(version)}&project=OTHER" +
                "&epics=${epics*.key.join(',')}").json

        then:
        suggested.status == 200
        suggested.json*.name.every { it.startsWith(suggestedKey + ' ') }
        stored.json*.name.every { it.startsWith('STORED ') }
        given.json*.name.every { it.startsWith('OTHER ') }
        given.json[0].keySet() as List == ['name', 'released', 'releaseDate']
        given.json*.released == given.json*.released.sort(false)
        given.json.findAll { it.released }.size() == 2
        !given.json[0].released
        !epics.isEmpty()
        epics.every { it.key.startsWith('OTHER-') && it.epicKey == null }
        epics[0].keySet() as List == ['key', 'summary', 'status', 'epicKey', 'updated']
        stories.every { it.epicKey in epics*.key }
        api.get("/api/products/$created.id/jira/epics?fixVersion=NOPE%209.9").json == []
        api.get("/api/products/$created.id/jira/stories?fixVersion=${enc(version)}&project=OTHER").json == []
    }

    def "a change is drafted from the epics and stories of a FixVersion, raised without change tasks and listed"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Payments ${uniqueCode()}"))
        def key = 'PAY' + created.id
        api.put("/api/products/$created.id/change-profile", [template: templateJson(jiraProjectKey: key),
                                                             tasks: tasksJson()])

        when:
        def versions = api.get("/api/products/$created.id/jira/versions").json
        def fixVersion = versions[0].name
        def epics = api.get("/api/products/$created.id/jira/epics?fixVersion=${enc(fixVersion)}").json
        def chosen = epics.take(2)*.key
        def stories = api.get("/api/products/$created.id/jira/stories?fixVersion=${enc(fixVersion)}" +
                "&epics=${chosen.join(',')}").json
        def template = templateJson(jiraProjectKey: key, 'riskAssessment.businessImpact': 'Medium')
        def preview = api.post('/api/changes/preview', change(created, fixVersion, chosen, stories*.key,
                [template: template]))

        then:
        fixVersion.startsWith(key + ' ')
        !versions[0].released
        epics.size() >= 2
        !stories.isEmpty()
        stories.every { it.epicKey in chosen }
        preview.status == 200
        preview.json.keySet() as List == CHANGE_KEYS
        [preview.json.id, preview.json.number, preview.json.url, preview.json.createdAt, preview.json.syncedAt,
         preview.json.syncProblem, preview.json.update, preview.json.version] == [null] * 8
        [preview.json.departmentId, preview.json.departmentName] == [3, 'Corporate Technology']
        preview.json.state == 'DRAFT'
        preview.json.workflow == []
        preview.json.fixVersion == fixVersion
        preview.json.schedule == scheduleJson(START)
        preview.json.openedBy == SIGNED_IN
        preview.json.template == raisedAs(template, fixVersion)
        preview.json.shortDescription == "$created.name $fixVersion: ${epics.take(2)*.summary.join('; ')}"
        preview.json.description.startsWith("Production release $fixVersion of $created.name ($created.code) in Corporate Technology.\n")
        !preview.json.description.contains('Change tasks')
        preview.json.description.contains("Scope from Jira project $key, FixVersion $fixVersion:\n${epics[0].key} ${epics[0].summary}")
        stories.every { preview.json.description.contains("- $it.key $it.summary") }
        preview.json.description.contains('Backout plan:\nRedeploy the previous release.\n')
        preview.json.description.contains('Privileged access: not needed.')
        preview.json.description.contains('Risk: Moderate\nNumber of BBH workgroups impacted: Single\n')
        preview.json.description.contains('Business impact: Medium')
        preview.json.tasks == []

        when:
        def raised = api.post('/api/changes', change(created, fixVersion, chosen, stories*.key,
                [shortDescription: ' Payments release 42 ', description: preview.json.description + '\nExtra.',
                 template: templateJson(jiraProjectKey: key, release: 'Payments 42'),
                 tasks: [[number: 'CTASK0000001', shortDescription: ' ', description: 'Deploy.']]]))

        then:
        raised.status == 201
        raised.header('Location') == "$api.baseUrl/api/changes/${raised.json.id}"
        raised.json.number ==~ /CHG\d{7}/
        raised.json.tasks == []
        raised.json.shortDescription == 'Payments release 42'
        raised.json.description.endsWith('\nExtra.')
        !raised.json.description.contains('Change tasks')
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PRODUCTION_CHANGE_TASK WHERE CHANGE_ID = ?', Integer,
                raised.json.id) == 0
        raised.json.fixVersion == fixVersion
        raised.json.schedule == scheduleJson(START)
        raised.json.template.release == 'Payments 42'
        raised.json.openedBy == SIGNED_IN
        raised.json.epicKeys == chosen
        raised.json.storyKeys == stories*.key
        raised.json.createdAt != null
        raised.json.state == 'DRAFT'
        raised.json.workflow == [[state: 'DRAFT', enteredAt: raised.json.createdAt]]
        raised.json.syncedAt == raised.json.createdAt
        raised.json.version == 0
        raised.json.update == null
        raised.json.keySet() as List == CHANGE_KEYS

        when:
        def opened = api.get("/api/changes/$raised.json.id").json
        def listed = api.get('/api/changes?departmentId=3').json

        then:
        opened.findAll { it.key != 'syncedAt' } == raised.json.findAll { it.key != 'syncedAt' }
        !Instant.parse(opened.syncedAt).isBefore(Instant.parse(raised.json.syncedAt))
        listed.find { it.id == raised.json.id }.findAll { it.key != 'syncedAt' } ==
                raised.json.findAll { it.key != 'syncedAt' }
        listed.every { it.departmentId == 3 }
        listed*.id == listed*.id.sort(false).reverse()
        api.get('/api/changes').json[0].id == raised.json.id
    }

    def "the changes are listed by the department that owns them"() {
        given:
        def corporate = createProduct(product(code: uniqueCode(), name: "Corporate ${uniqueCode()}"))
        def custody = createProduct(product(code: uniqueCode(), name: "Custody ${uniqueCode()}", departmentId: 4))
        def mine = raise(corporate)
        def theirs = raise(custody)

        when:
        def listed = api.get('/api/changes?departmentId=4').json

        then:
        theirs.departmentName == 'Custody'
        listed*.id.contains(theirs.id)
        !listed*.id.contains(mine.id)
        listed.every { it.departmentId == 4 && it.departmentName == 'Custody' }
        api.get('/api/changes?departmentId=999999').json == []
    }

    def "a product without stored ProTech defaults raises a change with the template it sends and the tasks it creates"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Fresh ${uniqueCode()}"))
        def template = templateJson(jiraProjectKey: 'FRESH', 'privilegedAccess.required': true,
                'privilegedAccess.users': [[user: 'Jane Smith', account: 'adm_jsmith']])
        def fixVersion = api.get("/api/products/$created.id/jira/versions?project=FRESH").json[0].name
        def epic = api.get("/api/products/$created.id/jira/epics?fixVersion=${enc(fixVersion)}&project=FRESH").json[0]

        when:
        def raised = api.post('/api/changes', change(created, fixVersion, [epic.key], [], [template: template]))
        def tasked = createTasks(raised.json as Map, changeTasksJson(1))

        then:
        raised.status == 201
        raised.json.template == raisedAs(template, fixVersion)
        raised.json.tasks == []
        tasked.tasks*.details*.shortDescription == ['Task 1 of the CertScanner release']
        tasked.template == raised.json.template
        raised.json.description.contains('Privileged access needed for: Jane Smith (adm_jsmith).')
        api.get("/api/products/$created.id/change-profile").json.version == null
    }

    def "a change of #refusal is refused with the field to fix"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Product ${uniqueCode()}"))
        def key = 'REG' + created.id
        def fixVersion = api.get("/api/products/$created.id/jira/versions?project=$key").json[0].name
        def epic = api.get("/api/products/$created.id/jira/epics?fixVersion=${enc(fixVersion)}&project=$key")
                .json[0].key

        def body = [template: templateJson(jiraProjectKey: key)] + edits
        body.template?.put('jiraProjectKey', key)

        when:
        def response = api.post('/api/changes', change(created, fixVersion, [epic], [], body))

        then:
        response.status == 400
        response.json.errors*.field == fields
        api.get('/api/changes').json.every { it.productId != created.id }

        where:
        refusal                       | edits                                                          || fields
        'no FixVersion'               | [fixVersion: ' ']                                              || ['fixVersion']
        'an installation in the past' | [schedule: scheduleJson(Instant.parse('2020-01-01T08:00:00Z'))] || ['schedule.installationStart']
        'an installation ending first' | [schedule: scheduleJson(START) + [installationEnd: START.toString()]] || ['schedule.installationEnd']
        'a validation before the end' | [schedule: scheduleJson(START) + [validationStart: START.toString()]] || ['schedule.validationStart']
        'no first usage'              | [schedule: scheduleJson(START) + [firstUsage: null]]           || ['schedule.firstUsage']
        'no schedule'                 | [schedule: null]                                               || ['schedule']
        'no template'                 | [template: null]                                               || ['template']
        'no backout plan'             | [template: templateJson('planning.backoutPlan': '')]           || ['template.planning.backoutPlan']
        'downtime without its window' | [template: templateJson(downtime: true)]                       || ['schedule.downtimeStart', 'schedule.downtimeEnd']
        'a window without downtime'   | [schedule: scheduleJson(START, true)]                          || ['schedule.downtimeStart', 'schedule.downtimeEnd']
        'a downtime ending first'     | [template: templateJson(downtime: true), schedule: scheduleJson(START, true) + [downtimeEnd: START.toString()]] || ['schedule.downtimeEnd']
        'an answer off its list'      | [template: templateJson('riskAssessment.backoutTesting': '15 minutes')] || ['template.riskAssessment.backoutTesting']
        'privileged access without users' | [template: templateJson('privilegedAccess.required': true)] || ['template.privilegedAccess.users']
        'no epics'                    | [epicKeys: []]                                                 || ['epicKeys']
        'an unknown epic'             | [epicKeys: ['NOPE-1']]                                         || ['epicKeys']
        'a too long short text'       | [shortDescription: 's' * 161]                                  || ['shortDescription']
    }

    def "a change of a product outside every department is refused on the product, previewed or raised"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Orphan ${uniqueCode()}"))
        def key = 'ORP' + created.id
        def fixVersion = api.get("/api/products/$created.id/jira/versions?project=$key").json[0].name
        def epic = api.get("/api/products/$created.id/jira/epics?fixVersion=${enc(fixVersion)}&project=$key")
                .json[0].key
        jdbc.update('UPDATE DSO_PRODUCT SET DEPARTMENT_ID = NULL WHERE ID = ?', created.id)
        def body = change(created, fixVersion, [epic], [], [template: templateJson(jiraProjectKey: key)])

        when:
        def previewed = api.post('/api/changes/preview', body)
        def raised = api.post('/api/changes', body)

        then:
        [previewed, raised]*.status == [400, 400]
        [previewed, raised]*.json*.errors == [[[field  : 'productId',
                                                 message: 'the product must be placed in a department in Beadle Admin first']]] * 2
        api.get('/api/changes').json.every { it.productId != created.id }
    }

    def "the preview accepts an installation start in the past, raising it does not"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Product ${uniqueCode()}"))
        def key = 'PRE' + created.id
        def fixVersion = api.get("/api/products/$created.id/jira/versions?project=$key").json[0].name
        def epic = api.get("/api/products/$created.id/jira/epics?fixVersion=${enc(fixVersion)}&project=$key")
                .json[0].key
        def past = change(created, fixVersion, [epic], [], [template: templateJson(jiraProjectKey: key),
                                                           schedule: scheduleJson(Instant.parse('2026-01-05T17:00:00Z'))])

        expect:
        api.post('/api/changes/preview', past).json.schedule.installationStart == '2026-01-05T17:00:00Z'
        api.post('/api/changes', past).json.errors == [[field: 'schedule.installationStart', message: 'must be in the future']]
    }

    def "the preview writes the texts before the schedule is planned, raising it does not"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Product ${uniqueCode()}"))
        def key = 'UNP' + created.id
        def fixVersion = api.get("/api/products/$created.id/jira/versions?project=$key").json[0].name
        def epic = api.get("/api/products/$created.id/jira/epics?fixVersion=${enc(fixVersion)}&project=$key").json[0]
        def unplanned = change(created, fixVersion, [epic.key], [], [template: templateJson(jiraProjectKey: key),
                                                                     schedule: [:]])

        when:
        def preview = api.post('/api/changes/preview', unplanned)

        then:
        preview.status == 200
        preview.json.shortDescription == "$created.name $fixVersion: $epic.summary"
        preview.json.description.contains('Installation not planned yet, post-install validation not planned yet,' +
                ' first usage not planned yet. No downtime.')
        api.post('/api/changes', unplanned).json.errors*.field == ['schedule.installationStart',
                                                                   'schedule.installationEnd',
                                                                   'schedule.validationStart',
                                                                   'schedule.validationEnd', 'schedule.firstUsage']
    }

    def "the change tasks of a raised change are created in ProTech one by one, numbered and shown when it is read"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Tasks ${uniqueCode()}")), [])
        def release = changeTaskJson(assignmentGroup: 'Release Management', shortDescription: ' Deploy the release ',
                description: 'Run the deployment jobs.', application: 'Payments', packages: 'payments-4.2.jar',
                backoutPackages: 'payments-4.1.jar', importance: '1 - Critical', additionalComments: 'Page on-call.')
        def generic = changeTaskJson([assignmentGroup: 'Service Desk', assignedTo: 'Grace Turner',
                                      configurationItem: 'Payments Gateway', shortDescription: 'Tell the users',
                                      description: 'Post the notice.', platform: 'Mainframe', application: 'Payments',
                                      packages: 'payments-4.2.jar'], START.plus(1, HOURS)) + [number: 'CTASK0000001']

        when:
        def tasked = api.post("/api/changes/$raised.id/tasks", tasksOf(raised, [release, generic]))

        then:
        raised.tasks == []
        tasked.status == 200
        tasked.json.keySet() as List == CHANGE_KEYS
        tasked.json.tasks.every { it.keySet() as List == TASK_KEYS && it.details.keySet() as List == TASK_DETAIL_KEYS }
        tasked.json.tasks*.number.every { it ==~ /CTASK\d{7}/ && it != 'CTASK0000001' }
        tasked.json.tasks*.number.toSet().size() == 2
        tasked.json.tasks*.subMap('details', 'start', 'approval', 'state') ==
                [[details : [assignmentGroup  : 'Release Management', assignedTo: null,
                             configurationItem: raised.template.configurationItem, platform: 'None',
                             application      : 'Payments', packages: 'payments-4.2.jar',
                             backoutPackages  : 'payments-4.1.jar', importance: null,
                             shortDescription : 'Deploy the release', description: 'Run the deployment jobs.',
                             additionalComments: 'Page on-call.'],
                  start   : START.plus(1, MINUTES).toString(), approval: NOT_YET_REQUESTED, state: 'OPEN'],
                 [details : [assignmentGroup  : 'Service Desk', assignedTo: 'Grace Turner',
                             configurationItem: 'Payments Gateway', platform: null, application: null, packages: null,
                             backoutPackages  : null, importance: MODERATE, shortDescription: 'Tell the users',
                             description      : 'Post the notice.', additionalComments: null],
                  start   : null, approval: NOT_YET_REQUESTED, state: 'OPEN']]
        tasked.json.findAll { !(it.key in ['tasks', 'version', 'editedVersion', 'syncedAt']) } ==
                raised.findAll { !(it.key in ['tasks', 'version', 'editedVersion', 'syncedAt']) }
        tasked.json.version > raised.version
        !tasked.json.description.contains('Change tasks')
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PRODUCTION_CHANGE_TASK WHERE CHANGE_ID = ?', Integer,
                raised.id) == 2

        when:
        def opened = api.get("/api/changes/$raised.id").json
        def listed = api.get('/api/changes?departmentId=3').json.find { it.id == raised.id }
        def more = api.post("/api/changes/$raised.id/tasks", tasksOf(opened, changeTasksJson(1)))

        then:
        opened.findAll { it.key != 'syncedAt' } == tasked.json.findAll { it.key != 'syncedAt' }
        listed.tasks == opened.tasks
        more.status == 200
        more.json.tasks*.number.take(2) == opened.tasks*.number
        more.json.tasks[2].number ==~ /CTASK\d{7}/
        more.json.tasks*.details*.shortDescription == ['Deploy the release', 'Tell the users',
                                                      'Task 1 of the CertScanner release']
        api.get("/api/changes/$raised.id").json.tasks == more.json.tasks
    }

    def "a #kind is created with the platform, application, importance and start that fit it"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Platform ${uniqueCode()}")), [])

        when:
        def tasked = api.post("/api/changes/$raised.id/tasks", tasksOf(raised, [changeTaskJson([assignmentGroup: group,
                platform: platform, application: 'Payments', importance: '2 - High'], start)]))

        then:
        tasked.status == 200
        tasked.json.tasks[0].details.subMap('assignmentGroup', 'platform', 'application', 'importance') ==
                [assignmentGroup: group] + expected
        tasked.json.tasks[0].start == planned?.toString()

        where:
        kind                                    | group                         | platform       | start                  || expected                                                               | planned
        'release task on OpenShift'             | 'Release Management'          | 'OpenShift'    | START.plus(2, HOURS)   || [platform: 'OpenShift', application: 'OCP', importance: null]          | START.plus(2, HOURS)
        'release task without a platform'       | 'Release Management'          | ' '            | null                   || [platform: 'None', application: 'Payments', importance: null]          | START.plus(1, MINUTES)
        'task of a release management team'     | 'CT release management team'  | 'Cognos/Motio' | START.plus(1, MINUTES) || [platform: 'Cognos/Motio', application: 'Payments', importance: null]  | START.plus(1, MINUTES)
        'generic task on OpenShift'             | 'OpenShift Platform Support'  | 'OpenShift'    | START.plus(1, HOURS)   || [platform: null, application: null, importance: '2 - High']            | null
    }

    def "change tasks with #problem are refused against the field to fix"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Invalid ${uniqueCode()}")), [])

        when:
        def response = api.post("/api/changes/$raised.id/tasks", tasksOf(raised, tasks) + edits)

        then:
        response.status == 400
        response.json.title == 'Validation failed'
        response.json.errors.collectEntries { [it.field, it.message] } == errors
        api.get("/api/changes/$raised.id").json.subMap('tasks', 'version') == [tasks: [], version: raised.version]
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PRODUCTION_CHANGE_TASK WHERE CHANGE_ID = ?', Integer,
                raised.id) == 0

        where:
        problem                                       | tasks                                                                                       | edits                || errors
        'a release task starting with the installation' | [changeTaskJson([assignmentGroup: 'Release Management'], START)]                          | [:]                  || ['tasks[0].start': 'must be at least a minute after the installation start']
        'a release task starting after the installation' | [changeTaskJson([assignmentGroup: 'Release Management'], START.plus(2, HOURS).plusSeconds(1))] | [:]           || ['tasks[0].start': 'must not be after the installation end']
        'a second task without assignment group'      | [changeTaskJson(), changeTaskJson(assignmentGroup: ' ')]                                    | [:]                  || ['tasks[1].details.assignmentGroup': 'must not be blank']
        'a task without description'                  | [changeTaskJson(description: '')]                                                           | [:]                  || ['tasks[0].details.description': 'must not be blank']
        'an importance off the list'                  | [changeTaskJson(importance: '6 - Someday')]                                                 | [:]                  || ['tasks[0].details.importance': 'must be one of 1 - Critical, 2 - High, 3 - Moderate, 4 - Low, 5 - Planning']
        'a platform off the list'                     | [changeTaskJson(assignmentGroup: 'Release Management', platform: 'Windows')]                | [:]                  || ['tasks[0].details.platform': 'must be one of None, Mainframe, Distributed, OpenShift, Cognos/Motio']
        'a short description over 160 bytes'          | [changeTaskJson(shortDescription: 'é' * 81)]                                                | [:]                  || ['tasks[0].details.shortDescription': 'is too long: it may take at most 160 bytes']
        'a task without details'                      | [[start: null]]                                                                             | [:]                  || ['tasks[0].details.assignmentGroup': 'must not be blank', 'tasks[0].details.shortDescription': 'must not be blank', 'tasks[0].details.description': 'must not be blank']
        'no tasks'                                    | []                                                                                          | [:]                  || [tasks: 'size must be between 1 and 50']
        'no task list'                                | null                                                                                        | [:]                  || [tasks: 'must not be null']
        'fifty-one tasks'                             | changeTasksJson(51)                                                                         | [:]                  || [tasks: 'size must be between 1 and 50']
        'no version'                                  | changeTasksJson(1)                                                                          | [version: null]      || [version: 'must not be null']
        'no department'                               | changeTasksJson(1)                                                                          | [departmentId: null] || [departmentId: 'must not be null']
    }

    def "a change holds at most 50 change tasks"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Full ${uniqueCode()}")), changeTasksJson(50))

        when:
        def response = api.post("/api/changes/$raised.id/tasks", tasksOf(raised, changeTasksJson(1)))

        then:
        raised.tasks*.number.toSet().size() == 50
        response.status == 400
        response.json.errors == [[field: 'tasks', message: "$raised.number may hold at most 50 change tasks".toString()]]
        api.get("/api/changes/$raised.id").json.tasks*.number == raised.tasks*.number
    }

    def "change tasks are refused #refusal"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Refused ${uniqueCode()}")))
        if (unowned) {
            jdbc.update('UPDATE DSO_PRODUCTION_CHANGE SET DEPARTMENT_ID = NULL WHERE ID = ?', raised.id)
        }
        Instant now = Instant.now()
        def number = closed ? adopt(raised.id, now.minus(9, DAYS), now.minus(6, DAYS), now.minus(5, DAYS))
                : raised.number

        when:
        def response = api.post("/api/changes/$raised.id/tasks", tasksOf(raised, changeTasksJson(1)) + edits)

        then:
        response.status == status
        response.json.title == (status == 403 ? 'Forbidden' : 'Conflict')
        response.json.detail == detail(number)
        api.get("/api/changes/$raised.id").json.tasks*.number == raised.tasks*.number

        where:
        refusal                                        | unowned | closed | edits             || status | detail
        'for another department'                       | false   | false  | [departmentId: 4] || 403    | { "Only Corporate Technology can change $it".toString() }
        'for a change no department owns'              | true    | false  | [:]               || 403    | { "No department owns $it, so it cannot be changed in Beadle".toString() }
        'at a version read before the last tasks came' | false   | false  | [version: 0]      || 409    | { 'The record was changed by someone else in the meantime. Reload it and apply your change again.' }
        'once ProTech has closed the change'           | false   | true   | [:]               || 409    | { "$it is closed in ProTech and can no longer be changed".toString() }
    }

    def "ProTech asks for the approval of the change tasks once the change reaches the change task approval"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Approval ${uniqueCode()}")))
        Instant now = Instant.now()

        when:
        def drafted = api.get("/api/changes/$raised.id").json
        adopt(raised.id, now.minus(9, MINUTES), now.plus(3, DAYS), now.plus(3, DAYS).plus(3, HOURS))
        def requested = api.get("/api/changes/$raised.id").json

        then:
        drafted.state == 'DRAFT'
        drafted.tasks*.approval == [NOT_YET_REQUESTED] * 2
        requested.state == 'CTASK_APPROVAL'
        requested.tasks*.approval == ['Requested'] * 2
        requested.tasks*.state == ['OPEN'] * 2
        requested.tasks*.number == raised.tasks*.number
    }

    def "a change without change tasks is updated and gets its tasks afterwards"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Untasked ${uniqueCode()}")), [])

        when:
        def updated = api.put("/api/changes/$raised.id", editOf(raised, [shortDescription: 'Renamed release']))
        def tasked = api.post("/api/changes/$raised.id/tasks", tasksOf(updated.json, changeTasksJson()))

        then:
        updated.status == 200
        updated.json.tasks == []
        updated.json.update.subMap('status', 'fields') == [status: 'APPLIED', fields: []]
        tasked.status == 200
        tasked.json.shortDescription == 'Renamed release'
        tasked.json.tasks*.details*.shortDescription == changeTasksJson()*.details*.shortDescription
        tasked.json.update == updated.json.update
    }

    def "an update keeps the release tasks inside the installation window it moves to"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Window ${uniqueCode()}")),
                [changeTaskJson(assignmentGroup: 'Release Management'), changeTaskJson()])
        def later = scheduleJson(START.plus(1, DAYS))

        when:
        def kept = api.put("/api/changes/$raised.id", editOf(raised, [schedule: later]))
        def moved = api.put("/api/changes/$raised.id", editOf(raised, [schedule: later,
                tasks: [raised.tasks[0] + [start: null], raised.tasks[1]]]))

        then:
        raised.tasks*.start == [START.plus(1, MINUTES).toString(), null]
        kept.status == 400
        kept.json.errors == [[field: 'tasks[0].start', message: 'must be at least a minute after the installation start']]
        moved.status == 200
        moved.json.tasks*.number == raised.tasks*.number
        moved.json.tasks*.start == [START.plus(1, DAYS).plus(1, MINUTES).toString(), null]
        moved.json.update.subMap('status', 'fields') == [status: 'APPLIED', fields: []]
        api.get("/api/changes/$raised.id").json.tasks == moved.json.tasks
    }

    def "its department updates an open change, ProTech applies it and Beadle shows it as applied"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Update ${uniqueCode()}")))
        def later = scheduleJson(START.plus(1, DAYS))
        def edit = editOf(raised, [shortDescription: ' Renamed release ', schedule: later,
                                   template: raised.template + [category: 'Hardware', jiraProjectKey: 'OTHER',
                                                                usersAffected: 'Ledger operators', risk: 'High'],
                                   tasks: [raised.tasks[0] + [details: raised.tasks[0].details +
                                                                       [shortDescription: 'Deploy it all']],
                                           changeTaskJson(assignmentGroup: 'Service Desk',
                                                   shortDescription: 'Check the audit trail',
                                                   description: 'Open it.')]])

        when:
        def updated = api.put("/api/changes/$raised.id", edit)

        then:
        updated.status == 200
        updated.json.keySet() as List == CHANGE_KEYS
        updated.json.shortDescription == 'Renamed release'
        updated.json.schedule == later
        updated.json.template.category == 'Hardware'
        updated.json.template.usersAffected == 'Ledger operators'
        updated.json.template.risk == 'Moderate'
        updated.json.template.jiraProjectKey == raised.template.jiraProjectKey
        updated.json.tasks*.details*.shortDescription == ['Deploy it all', 'Check the audit trail',
                                                          raised.tasks[1].details.shortDescription]
        updated.json.tasks*.state == ['OPEN', 'OPEN', 'CANCELED']
        updated.json.tasks[0].number == raised.tasks[0].number
        updated.json.tasks[1].number ==~ /CTASK\d{7}/
        updated.json.tasks[1].details.subMap('assignmentGroup', 'configurationItem', 'importance') ==
                [assignmentGroup: 'Service Desk', configurationItem: raised.template.configurationItem,
                 importance: MODERATE]
        updated.json.tasks[2].number == raised.tasks[1].number
        updated.json.update.subMap('status', 'departmentName', 'fields', 'message') ==
                [status: 'APPLIED', departmentName: 'Corporate Technology', fields: [], message: null]
        !Instant.parse(updated.json.update.checkedAt).isBefore(Instant.parse(updated.json.update.requestedAt))
        updated.json.syncProblem == null
        updated.json.version == raised.version + 2

        when:
        def opened = api.get("/api/changes/$raised.id").json
        def stale = api.put("/api/changes/$raised.id", edit)

        then:
        opened.findAll { it.key != 'syncedAt' } == updated.json.findAll { it.key != 'syncedAt' }
        stale.status == 409
    }

    def "a schedule change ProTech does not take while the installation runs stays pending with its fields"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Running ${uniqueCode()}")))
        Instant now = Instant.now()
        def number = adopt(raised.id, now.minus(2, DAYS), now.minus(1, HOURS), now.plus(2, HOURS))
        def moved = scheduleJson(now.plus(1, HOURS).truncatedTo(HOURS).plus(1, HOURS))

        when:
        def opened = api.get("/api/changes/$raised.id").json
        def updated = api.put("/api/changes/$raised.id", editOf(opened, [schedule: moved,
                                                                         shortDescription: 'Moved release']))
        def waiting = api.get("/api/changes/$raised.id").json
        def refused = api.put("/api/changes/$raised.id", editOf(waiting, [shortDescription: 'Again']))
        def untasked = api.post("/api/changes/$raised.id/tasks", tasksOf(waiting, [changeTaskJson()]))

        then:
        opened.number == number
        opened.state == 'IMPLEMENTATION'
        opened.tasks*.state == ['WORK_IN_PROGRESS'] * 2
        opened.tasks*.approval == ['Approved'] * 2
        opened.workflow*.state == ['DRAFT', 'BUSINESS_APPROVAL', 'PRIMARY_APPROVAL', 'SECONDARY_APPROVAL',
                                   'CTASK_APPROVAL', 'IMPLEMENTATION']
        updated.status == 200
        updated.json.update.status == 'PENDING'
        updated.json.update.fields == SCHEDULE_PATHS
        updated.json.schedule == moved
        updated.json.shortDescription == 'Moved release'
        waiting.update.status == 'PENDING'
        refused.status == 409
        refused.json.detail == "The last update of $number is still waiting for ProTech; change it again once ProTech has applied it".toString()
        untasked.status == 409
        untasked.json.detail == refused.json.detail
        api.get("/api/changes/$raised.id").json.subMap('shortDescription', 'tasks') ==
                [shortDescription: 'Moved release', tasks: waiting.tasks]
    }

    def "a change ProTech has closed is synced when opened and can no longer be updated"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Closed ${uniqueCode()}")))
        Instant now = Instant.now()
        def number = adopt(raised.id, now.minus(9, DAYS), now.minus(6, DAYS), now.minus(5, DAYS))

        when:
        def opened = api.get("/api/changes/$raised.id").json
        def refused = api.put("/api/changes/$raised.id", editOf(opened, [shortDescription: 'Too late']))

        then:
        opened.state == 'CLOSED'
        opened.workflow*.state.last() == 'CLOSED'
        opened.tasks*.state == ['CLOSED'] * 2
        opened.tasks*.approval == ['Approved'] * 2
        opened.version == raised.version + 1
        refused.status == 409
        refused.json.detail == "$number is closed in ProTech and can no longer be changed".toString()
        api.get("/api/changes/$raised.id").json.shortDescription == raised.shortDescription
    }

    def "an update is refused #refusal"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Refused ${uniqueCode()}")))
        if (unowned) {
            jdbc.update('UPDATE DSO_PRODUCTION_CHANGE SET DEPARTMENT_ID = NULL WHERE ID = ?', raised.id)
        }

        when:
        def response = api.put("/api/changes/$raised.id", editOf(raised, edits(raised)))

        then:
        response.status == status
        response.json.title == (status == 400 ? 'Validation failed' : 'Forbidden')
        (status == 400 ? response.json.errors*.field : response.json.detail) == expected(raised)
        api.get("/api/changes/$raised.id").json.version == raised.version

        where:
        refusal                              | unowned | edits                                                     || status | expected
        'for another department'             | false   | { [departmentId: 4] }                                     || 403    | { "Only Corporate Technology can change $it.number".toString() }
        'for a change no department owns'    | true    | { [departmentId: 3] }                                     || 403    | { "No department owns $it.number, so it cannot be changed in Beadle".toString() }
        'without a department'               | false   | { [departmentId: null] }                                  || 400    | { ['departmentId'] }
        'without a version'                  | false   | { [version: null] }                                       || 400    | { ['version'] }
        'without a task list'                | false   | { [tasks: null] }                                         || 400    | { ['tasks'] }
        'with a blank short description'     | false   | { [shortDescription: ' '] }                               || 400    | { ['shortDescription'] }
        'with a task of another change'      | false   | { [tasks: [changeTaskJson() + [number: 'CTASK0000001']]] } || 400   | { ['tasks[0].number'] }
        'with a task without assignment group' | false | { Map change -> [tasks: [change.tasks[0] + [details: change.tasks[0].details + [assignmentGroup: ' ']]]] } || 400 | { ['tasks[0].details.assignmentGroup'] }
        'with a release task starting with the installation' | false | { [tasks: [changeTaskJson([assignmentGroup: 'Release Management'], START)]] } || 400 | { ['tasks[0].start'] }
        'with fifty-one tasks'               | false   | { [tasks: changeTasksJson(51)] }                          || 400    | { ['tasks'] }
        'with a task listed twice'           | false   | { Map change -> [tasks: [change.tasks[0], change.tasks[0]]] } || 400 | { ['tasks[1].number'] }
        'with an installation in the past'   | false   | { [schedule: scheduleJson(Instant.parse('2026-01-05T17:00:00Z'))] } || 400 | { ['schedule.installationStart'] }
    }

    def "a change outlives its product and Jira refuses requests without a FixVersion or with a broken project"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Product ${uniqueCode()}"))
        def key = 'OLD' + created.id
        def fixVersion = api.get("/api/products/$created.id/jira/versions?project=$key").json[0].name
        def epic = api.get("/api/products/$created.id/jira/epics?fixVersion=${enc(fixVersion)}&project=$key")
                .json[0].key
        api.put("/api/products/$created.id/change-profile", [template: templateJson(jiraProjectKey: key),
                                                             tasks: tasksJson()])
        def raised = createTasks(api.post('/api/changes', change(created, fixVersion, [epic], [],
                [template: templateJson(jiraProjectKey: key)])).json as Map, changeTasksJson())

        when:
        def deleted = api.delete("/api/products/$created.id")
        def kept = api.get("/api/changes/$raised.id").json

        then:
        deleted.status == 204
        kept.productId == null
        kept.productName == created.name
        kept.departmentId == 3
        kept.fixVersion == fixVersion
        kept.tasks*.number == raised.tasks*.number
        kept.tasks*.number.size() == 2
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_CHANGE_PROFILE WHERE PRODUCT_ID = ?', Integer, created.id) == 0
        api.get("/api/products/$created.id/jira/versions").status == 404

        and:
        api.get('/api/products/1/jira/epics').json.errors*.field == ['fixVersion']
        api.get('/api/products/1/jira/epics?fixVersion=%20').json.errors*.field == ['fixVersion']
        api.get('/api/products/1/jira/stories?epics=CERT-1&fixVersion=').json.errors*.field == ['fixVersion']
        api.get('/api/products/1/jira/versions?project=ce-rt').json.errors*.field == ['project']
        api.get('/api/changes/99999').status == 404
        api.put('/api/changes/99999', editOf(raised, [:])).status == 404
        api.post('/api/changes/99999/tasks', tasksOf(raised, changeTasksJson(1))).status == 404
        api.get('/api/changes/integrations').json == [jiraConnected: false, serviceNowConnected: false]
    }

    private String adopt(long id, Instant raisedAt, Instant installationStart, Instant validationEnd) {
        String number = 'CHG9' + String.valueOf(id).padLeft(6, '0')
        jdbc.update('''UPDATE DSO_PRODUCTION_CHANGE SET CHANGE_NUMBER = ?, CREATED_AT = ?, INSTALLATION_START = ?,
                INSTALLATION_END = ?, VALIDATION_START = ?, VALIDATION_END = ?, FIRST_USAGE = ? WHERE ID = ?''',
                number, utc(raisedAt), utc(installationStart), utc(installationStart.plus(1, HOURS)),
                utc(installationStart.plus(1, HOURS)), utc(validationEnd), utc(validationEnd.plus(8, HOURS)), id)
        number
    }

    private static LocalDateTime utc(Instant instant) {
        LocalDateTime.ofInstant(instant, UTC)
    }
}
