package com.bbh.itss.dso.portal.gui.support

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

import static com.bbh.itss.dso.portal.gui.support.StubApi.SIGNED_IN_USER
import static com.bbh.itss.dso.portal.gui.support.StubApi.fixture
import static com.bbh.itss.dso.portal.gui.support.StubResponse.json
import static com.bbh.itss.dso.portal.gui.support.StubResponse.problem
import static java.lang.String.format
import static java.time.Instant.parse
import static java.time.LocalDate.now
import static java.time.temporal.ChronoUnit.DAYS
import static java.time.temporal.ChronoUnit.HOURS
import static java.time.temporal.ChronoUnit.MINUTES
import static java.time.temporal.ChronoUnit.SECONDS

final class ChangeStubs {

    static final String STALE = 'The record was changed by someone else in the meantime. Reload it and apply your change again.'

    static final String NOT_APPLIED = 'A minute later ProTech still held its own values, so Beadle shows those.'

    static final List<String> SCHEDULE = ['installationStart', 'installationEnd', 'validationStart', 'validationEnd', 'firstUsage',
                                          'downtimeStart', 'downtimeEnd']

    static final List<String> EDITABLE = ['requestedFor', 'requestedBy', 'department', 'assignmentGroup', 'category',
                                          'assignedTo', 'release', 'configurationItem', 'incident', 'directBusinessService',
                                          'problem', 'affectedClients', 'usersAffected', 'description', 'approvers', 'downtime',
                                          'planning', 'privilegedAccess', 'riskAssessment', 'secureCodingTicket']

    static final int MAX_LOOKUPS = 20

    static final Map OPTIONS = fixture('change-options.json') as Map

    static final Map LOOKUPS = fixture('lookups.json') as Map

    static final Map PLANNING = [
            testSummary       : 'Unit, smoke, regression and performance tests and the security scans passed on QC.',
            implementationPlan: 'Work through the change tasks in the order listed.',
            validationPlan    : 'Run the smoke tests of the DevSecOps pipeline against production and check the monitoring.',
            backoutPlan       : 'Redeploy the previous release from Nexus.',
            firstUsePlan      : 'The business owner confirms the first use of the release in production.']

    static final Map CERT_TEMPLATE = withRisk([
            jiraProjectKey   : 'CERT', requestedFor: null, requestedBy: null, department: null,
            assignmentGroup  : 'Technology Architecture', category: 'Application', assignedTo: null, type: 'STANDARD',
            release          : null, configurationItem: 'CertScanner', incident: null,
            directBusinessService: 'Certificate Management', problem: null, affectedClients: null, usersAffected: null,
            description      : 'Scans TLS certificates.',
            approvers        : [businessApprover: 'Grace Turner', l1Manager: 'Olivia Bennett', l2Manager: 'James Carter'],
            downtime         : false,
            timing           : [installationStart: '18:00', installationHours: 2, validationHours: 1],
            planning         : PLANNING,
            privilegedAccess : [required: false, users: []],
            riskAssessment   : [bbhWorkgroups: 'Single', changeComplexity: 'Simple', bbhUsers: '5-25',
                                validationComplexity: 'Simple', bbhApplications: 'Single',
                                backoutTesting: 'Less than 30 minutes', clientsOutsideBbh: 'No clients',
                                platformStatus: 'Existing', businessImpact: 'Low'],
            secureCodingTicket: null])

    static final List<Map> CERT_TASKS = [
            taskText('Deploy CertScanner to production', 'Deploy the release of CertScanner with its deployment jobs.'),
            taskText('Validate CertScanner in production', 'Run the smoke tests of CertScanner and confirm the release.')]

    static final List<Map> CERT_ISSUES = [
            issue('CERT-120', 'Expiry alerts for certificates', null, '4.2', 3),
            issue('CERT-121', 'E-mail the certificate owner', 'CERT-120', '4.2', 5),
            issue('CERT-122', 'Teams alert on expiry', 'CERT-120', '4.2', 8),
            issue('CERT-130', 'Audit trail of user actions', null, '4.3', 12),
            issue('CERT-131', 'Record each change with its user', 'CERT-130', '4.2', 14),
            issue('CERT-132', 'Export the audit trail', 'CERT-130', '4.3', 15),
            issue('CERT-140', 'Upgrade to Java 21', null, '4.1', 40)]

    static final String RELEASE_DATE = now().plusDays(14).toString()

    private ChangeStubs() {
    }

    static ProTech install(StubApi api) {
        def profiles = new ConcurrentHashMap<Integer, Map>([1: [version: 2, template: CERT_TEMPLATE, tasks: CERT_TASKS]])
        def protech = new ProTech()
        def projectOf = { RecordedRequest request, String id ->
            request.params().project ?: (profiles[id as int]?.template ?: suggested(id)).jiraProjectKey
        }

        api.get('/api/changes/integrations') { [jiraConnected: false, serviceNowConnected: false] }
        api.get('/api/changes/options') { OPTIONS }
        api.get('/api/lookups/([^/]+)') { RecordedRequest request, List<String> kinds -> lookup(kinds[0], request.params().q) }
        api.get('/api/changes') { RecordedRequest request -> protech.list(request.params().departmentId) }
        api.get('/api/changes/(\\d+)') { RecordedRequest request, List<String> ids ->
            def found = protech.find(ids[0])
            found ? json(protech.read(found)) : problem(404, 'Not found', "Change ${ids[0]} does not exist")
        }
        api.on('PUT', '/api/changes/(\\d+)') { RecordedRequest request, List<String> ids ->
            def found = protech.find(ids[0])
            found ? protech.update(found, request.json() as Map) : problem(404, 'Not found', "Change ${ids[0]} does not exist")
        }
        api.get('/api/change-profiles') {
            profiles.collect { id, stored ->
                [productId: id, productName: product(id).name, version: stored.version, updatedAt: '2026-10-05T12:00:00Z']
            }.sort { it.productName }
        }
        api.get('/api/products/(\\d+)/change-profile') { RecordedRequest request, List<String> ids ->
            def stored = profiles[ids[0] as int]
            [productId: ids[0] as int, productName: product(ids[0]).name, version: stored?.version,
             updatedAt: stored ? '2026-10-05T12:00:00Z' : null, template: stored?.template ?: suggested(ids[0]),
             tasks    : stored?.tasks ?: suggestedTasks(ids[0])]
        }
        api.on('PUT', '/api/products/(\\d+)/change-profile') { RecordedRequest request, List<String> ids ->
            def stored = profiles[ids[0] as int]
            Map asked = request.json() as Map
            if (asked.version != stored?.version) {
                return problem(409, 'Conflict', STALE)
            }
            def saved = [version: stored ? stored.version + 1 : 0, template: withRisk(asked.template as Map), tasks: asked.tasks]
            profiles[ids[0] as int] = saved
            [productId: ids[0] as int, productName: product(ids[0]).name, updatedAt: '2026-10-07T09:00:00Z'] + saved
        }
        api.get('/api/products/(\\d+)/jira/versions') { RecordedRequest request, List<String> ids ->
            def project = projectOf(request, ids[0])
            [[name: "$project 4.2".toString(), released: false, releaseDate: RELEASE_DATE],
             [name: "$project 4.3".toString(), released: false, releaseDate: null],
             [name: "$project 4.1".toString(), released: true, releaseDate: now().minusDays(20).toString()]]
        }
        api.get('/api/products/(\\d+)/jira/epics') { RecordedRequest request, List<String> ids ->
            def fixVersion = request.params().fixVersion?.trim()
            if (!fixVersion) {
                return problem(400, 'Bad Request', 'fixVersion must not be blank')
            }
            def issues = issuesOf(projectOf(request, ids[0]))
            def carrying = issues.findAll { it.fixVersion == fixVersion }
            def epics = issues.findAll { it.epicKey == null && (it in carrying || carrying.any { story -> story.epicKey == it.key }) }
            epics.collect { jiraIssue(it) }
        }
        api.get('/api/products/(\\d+)/jira/stories') { RecordedRequest request, List<String> ids ->
            def epics = request.params().epics?.split(',') as List ?: []
            issuesOf(projectOf(request, ids[0]))
                    .findAll { it.epicKey in epics && it.fixVersion == request.params().fixVersion }
                    .collect { jiraIssue(it) }
        }
        api.on('POST', '/api/changes/preview') { RecordedRequest request -> draft(request.json() as Map) }
        api.on('POST', '/api/changes') { RecordedRequest request -> json(protech.raise(request.json() as Map), 201) }
        protech
    }

    static Map suggested(Object id) {
        def product = product(id)
        def letters = (product.code as String).replaceAll(/[^A-Z0-9]/, '')
        [jiraProjectKey    : letters.size() <= 6 ? letters : letters.take(4), requestedFor: null, requestedBy: null,
         department        : null, assignmentGroup: product.ownerTeam ?: "$product.name Support", category: 'Application',
         assignedTo        : null, type: 'STANDARD', release: null, configurationItem: product.name, incident: null,
         directBusinessService: null, problem: null, risk: null, affectedClients: null, usersAffected: null,
         description       : product.description,
         approvers         : [businessApprover: null, l1Manager: null, l2Manager: null], downtime: false,
         timing            : [installationStart: '18:00', installationHours: 2, validationHours: 1], planning: PLANNING,
         privilegedAccess  : [required: false, users: []],
         riskAssessment    : (OPTIONS.risk as Map).collectEntries { question, answers -> [question, null] },
         secureCodingTicket: null]
    }

    static Map withRisk(Map template) {
        template + [risk: riskOf(template.riskAssessment as Map)]
    }

    static String riskOf(Map assessment) {
        def answers = (OPTIONS.risk as Map<String, List<String>>).findResults { question, options ->
            def at = options.indexOf(assessment?.get(question))
            at < 0 ? null : [at: at, last: options.size() - 1]
        }
        if (!answers) {
            return null
        }
        answers.any { it.at == it.last } ? 'High' : answers.any { it.at > 0 } ? 'Moderate' : 'Low'
    }

    static Object lookup(String kind, String query) {
        def entries = LOOKUPS[kind] as List<Map>
        if (entries == null) {
            return problem(404, 'Not found', "There is no lookup of $kind")
        }
        def text = query?.trim()?.toLowerCase() ?: ''
        entries.findAll { [it.value, it.detail].any { field -> field?.toString()?.toLowerCase()?.contains(text) } }
                .take(MAX_LOOKUPS)
    }

    static List<Map> suggestedTasks(Object id) {
        def name = product(id).name
        [taskText("Deploy $name to production", "Deploy the release of $name in the change window with its deployment jobs, " +
                'then run the smoke tests of the DevSecOps pipeline and record the result in this task.'),
         taskText("Validate $name in production", "Run the post-install validation of $name: the smoke tests and the monitoring, " +
                 'then confirm the release with the business owner in this task.')]
    }

    static Map draft(Map asked) {
        def product = product(asked.productId)
        def template = asked.template as Map
        def issues = issuesOf(template.jiraProjectKey as String)
        def epics = issues.findAll { it.key in asked.epicKeys }
        def stories = issues.findAll { it.key in asked.storyKeys }
        def department = departmentName(product.departmentId)
        [id              : null, number: null, productId: product.id, productCode: product.code, productName: product.name,
         departmentId    : product.departmentId, departmentName: department,
         fixVersion      : asked.fixVersion, schedule: asked.schedule,
         shortDescription: "$product.name $asked.fixVersion: ${epics*.summary.join('; ')}".toString(),
         description     : "Production release of $product.name ($product.code), FixVersion $asked.fixVersion.\n"
                 + "Change tasks: ${(asked.tasks as List<Map>)*.shortDescription.join('; ')}.\n\nEpics:\n"
                 + epics.collect { "$it.key $it.summary ($it.status)" }.join('\n') + '\n\nStories:\n'
                 + stories.collect { "$it.key $it.summary" }.join('\n'),
         template        : withRisk(template + [release     : template.release ?: asked.fixVersion,
                                                requestedFor: template.requestedFor ?: SIGNED_IN_USER,
                                                requestedBy : template.requestedBy ?: SIGNED_IN_USER,
                                                assignedTo  : template.assignedTo ?: SIGNED_IN_USER,
                                                department  : template.department ?: department]),
         epicKeys        : asked.epicKeys, storyKeys: asked.storyKeys,
         tasks           : (asked.tasks as List<Map>).collect { task(null, it, 'OPEN') },
         url             : null, state: 'DRAFT', workflow: [], syncedAt: null, syncProblem: null, update: null,
         version         : null, editedVersion: null, createdAt: null, openedBy: SIGNED_IN_USER]
    }

    static List<String> unapplied(Map requested, Map held) {
        def differing = { List<String> keys, Map wanted, Map stored, Closure<Boolean> same ->
            keys.findAll { !same(wanted[it], stored[it]) }
        }
        def paths = differing(['shortDescription', 'description'], requested, held) { a, b -> a == b } +
                differing(SCHEDULE, requested.schedule as Map, held.schedule as Map) { a, b ->
                    a == b || (a && b && parse(a as String) == parse(b as String))
                }.collect { "schedule.$it".toString() } +
                differing(EDITABLE, requested.template as Map, held.template as Map) { a, b -> a == b }
                        .collect { "template.$it".toString() }
        texts(requested.tasks as List<Map>) == texts(held.tasks as List<Map>) ? paths : paths + 'tasks'
    }

    private static List<List> texts(List<Map> tasks) {
        tasks.findAll { it.state != 'CANCELED' }.collect { [it.shortDescription, it.description] }
    }

    private static Map product(Object id) {
        fixture("product-${id}.json") as Map
    }

    private static String departmentName(Object id) {
        (fixture('departments.json') as List<Map>).find { it.id == id }?.name
    }

    private static List<Map> issuesOf(String project) {
        CERT_ISSUES.collect {
            it + [key        : it.key.replace('CERT', project), epicKey: it.epicKey?.replace('CERT', project),
                  fixVersion: "$project $it.fixVersion".toString()]
        }
    }

    private static Map jiraIssue(Map issue) {
        issue.subMap('key', 'summary', 'status', 'epicKey', 'updated')
    }

    private static Map taskText(String shortDescription, String description) {
        [shortDescription: shortDescription, description: description]
    }

    private static Map task(String number, Map text, String state) {
        [number: number, shortDescription: text.shortDescription, description: text.description, state: state]
    }

    private static Map issue(String key, String summary, String epicKey, String fixVersion, int daysAgo) {
        [key       : key, summary: summary, status: daysAgo < 10 ? 'In Review' : 'Done', epicKey: epicKey,
         fixVersion: fixVersion, updated: now().minusDays(daysAgo).toString()]
    }

    private static String stamp(Instant time = Instant.now()) {
        time.truncatedTo(SECONDS).toString()
    }

    static final class ProTech {

        volatile boolean applying = true
        final List<Map> changes = new CopyOnWriteArrayList<Map>()
        final Map<Integer, List<Map>> canceling = new ConcurrentHashMap<>()
        final AtomicInteger changeNumbers = new AtomicInteger(31001)
        final AtomicInteger taskNumbers = new AtomicInteger(320000)

        ProTech() {
            def at = Instant.now().truncatedTo(MINUTES)
            def today = at.truncatedTo(DAYS)
            changes << seed(1, 'CHG0030990', 1, 'CERT 4.0', at.minus(10, DAYS), today.minus(7, DAYS).plus(17, HOURS),
                    ['DRAFT', 'BUSINESS_APPROVAL', 'PRIMARY_APPROVAL', 'SECONDARY_APPROVAL', 'CTASK_APPROVAL', 'IMPLEMENTATION',
                     'CLOSED'], 'CLOSED', 6)
            changes << seed(2, 'CHG0030995', 1, 'CERT 4.1.1', at.minus(3, DAYS), at.minus(1, HOURS),
                    ['DRAFT', 'BUSINESS_APPROVAL', 'PRIMARY_APPROVAL', 'SECONDARY_APPROVAL', 'CTASK_APPROVAL', 'IMPLEMENTATION'],
                    'WORK_IN_PROGRESS', 5) + [update: [status       : 'NOT_APPLIED', requestedAt: stamp(at.minus(30, MINUTES)),
                                                      departmentName: 'Corporate Technology',
                                                      fields        : ['schedule.installationStart', 'schedule.installationEnd'],
                                                      message       : NOT_APPLIED, checkedAt: stamp(at.minus(29, MINUTES))]]
            changes << seed(3, 'CHG0031000', 2, 'PAYHUB 4.2', at.minus(30, MINUTES), at.plus(20, HOURS),
                    ['DRAFT', 'BUSINESS_APPROVAL', 'PRIMARY_APPROVAL', 'SECONDARY_APPROVAL', 'CTASK_APPROVAL', 'ESCALATED_APPROVAL'],
                    'OPEN', 4)
            changes << seed(4, 'CHG0031001', 1, 'CERT 4.1', at.minus(1, DAYS), today.plus(3, DAYS).plus(17, HOURS),
                    ['DRAFT', 'BUSINESS_APPROVAL', 'PRIMARY_APPROVAL', 'SECONDARY_APPROVAL'], 'OPEN', 3)
        }

        List<Map> list(String departmentId) {
            changes.findAll { !departmentId || it.departmentId == departmentId as int }
                    .collect { it.state == 'CLOSED' ? it : read(it) }
                    .sort { a, b -> b.id <=> a.id }
        }

        Map find(Object id) {
            changes.find { it.id == id as int }
        }

        Map read(Map change) {
            if (applying && (change.update as Map)?.status == 'PENDING') {
                change.tasks = (change.tasks as List<Map>).collect {
                    it.number ? it : it + [number: format('CTASK%07d', taskNumbers.incrementAndGet())]
                } + (canceling.remove(change.id as int) ?: [])
                change.update = (change.update as Map) + [status: 'APPLIED', fields: [], checkedAt: stamp()]
                change.version = (change.version as int) + 1
            }
            change.syncedAt = stamp()
            change
        }

        Object update(Map change, Map asked) {
            if (asked.version != null && (asked.version < change.editedVersion || asked.version > change.version)) {
                return problem(409, 'Conflict', STALE)
            }
            if (asked.departmentId != change.departmentId) {
                return problem(403, 'Forbidden', "Only $change.departmentName can change $change.number")
            }
            if ((change.update as Map)?.status == 'PENDING') {
                return problem(409, 'Conflict', "The last update of $change.number is still waiting for ProTech; " +
                        'change it again once ProTech has applied it')
            }
            if (change.state == 'CLOSED') {
                return problem(409, 'Conflict', "$change.number is closed in ProTech and can no longer be changed")
            }
            def held = change.tasks as List<Map>
            def numbers = (asked.tasks as List<Map>)*.number.findAll()
            def tasks = (asked.tasks as List<Map>).collect { wanted ->
                task(wanted.number as String, wanted, held.find { it.number && it.number == wanted.number }?.state as String ?: 'OPEN')
            }
            def requested = [shortDescription: asked.shortDescription, description: asked.description,
                             schedule        : asked.schedule,
                             template        : withRisk((asked.template as Map) +
                                     (change.template as Map).subMap('jiraProjectKey', 'type', 'timing') +
                                     [release: (asked.template as Map).release ?: change.fixVersion]),
                             tasks           : tasks]
            def fields = unapplied(requested, change)
            canceling[change.id as int] = held.findAll { it.number && !(it.number in numbers) }.collect { it + [state: 'CANCELED'] }
            def version = (change.version as int) + 1
            change.putAll(requested + [update       : [status : 'PENDING', requestedAt: stamp(), departmentName: change.departmentName,
                                                       fields : fields, message: null, checkedAt: stamp()],
                                       version      : version, editedVersion: fields ? version : change.editedVersion,
                                       syncedAt     : stamp()])
            change
        }

        void advance(Object id, String state) {
            def change = find(id)
            change.state = state
            change.workflow = (change.workflow as List<Map>) + [state: state, enteredAt: stamp()]
            change.version = (change.version as int) + 1
        }

        Map raise(Map asked) {
            def number = "CHG00${changeNumbers.incrementAndGet()}".toString()
            def drafted = draft(asked)
            def raisedAt = stamp()
            def change = drafted + [
                    id              : (changes*.id.max() as int) + 1, number: number, createdAt: raisedAt,
                    shortDescription: asked.shortDescription ?: drafted.shortDescription,
                    description     : asked.description ?: drafted.description,
                    tasks           : (drafted.tasks as List<Map>).withIndex().collect { task, index ->
                        task + [number: format('CTASK%07d', (number.drop(3) as int) * 10 + index + 1)]
                    },
                    workflow        : [[state: 'DRAFT', enteredAt: raisedAt]], syncedAt: raisedAt, version: 0, editedVersion: 0]
            changes << change
            change
        }

        private static Map seed(int id, String number, int productId, String fixVersion, Instant raisedAt, Instant start,
                                List<String> stages, String taskState, int version) {
            def product = product(productId)
            def template = productId == 1 ? CERT_TEMPLATE : suggested(productId)
            def project = template.jiraProjectKey
            def schedule = [installationStart: stamp(start), installationEnd: stamp(start.plus(2, HOURS)),
                            validationStart  : stamp(start.plus(2, HOURS)), validationEnd: stamp(start.plus(3, HOURS)),
                            firstUsage       : stamp(start.plus(3, HOURS)), downtimeStart: null, downtimeEnd: null]
            def texts = productId == 1 ? CERT_TASKS : suggestedTasks(productId)
            def drafted = draft([productId: product.id, fixVersion: fixVersion, epicKeys: ["$project-140".toString()],
                                 storyKeys: [], schedule: schedule, template: template, tasks: texts])
            def entered = stages.withIndex().collect { stage, index ->
                [state    : stage,
                 enteredAt: stamp(stage == 'CLOSED' ? start.plus(3, HOURS) : raisedAt.plus(2 * index, MINUTES))]
            }
            drafted + [id      : id, number: number, createdAt: stamp(raisedAt), state: stages.last(), workflow: entered,
                       tasks   : texts.withIndex().collect { text, index ->
                           task(format('CTASK%07d', (number.drop(3) as int) * 10 + index + 1), text, taskState)
                       },
                       syncedAt: stamp(raisedAt), version: version, editedVersion: version]
        }
    }
}
