package com.bbh.itss.dso.portal.gui.support

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

import static com.bbh.itss.dso.portal.gui.support.StubApi.fixture
import static com.bbh.itss.dso.portal.gui.support.StubResponse.json
import static com.bbh.itss.dso.portal.gui.support.StubResponse.problem
import static java.time.LocalDate.now

final class ChangeStubs {

    static final Map PLANNING = [
            testSummary       : 'Unit, smoke, regression and performance tests and the security scans passed on QC.',
            implementationPlan: 'Deploy each service with its change task, in the order listed.',
            validationPlan    : 'Run the smoke tests of the DevSecOps pipeline against production and check the monitoring of each service.',
            backoutPlan       : 'Redeploy the previous release of each service from Nexus.',
            firstUsePlan      : 'The business owner confirms the first use of the release in production.']

    static final Map CERT_TEMPLATE = [
            jiraProjectKey   : 'CERT', assignmentGroup: 'Technology Architecture', category: 'Software', type: 'NORMAL',
            configurationItem: 'CertScanner', release: null, incident: null, problem: null, affectedClients: null,
            description      : 'Scans TLS certificates.',
            approvers        : [l1Manager: 'Olivia Bennett', l2Manager: 'James Carter', businessApprover: 'Grace Turner'],
            downtime         : false,
            timing           : [installationStart: '18:00', installationHours: 2, validationHours: 1],
            planning         : PLANNING,
            privilegedAccess : [required: false, users: []],
            riskAssessment   : [bbhWorkgroups: 1, bbhUsers: 25, bbhApplications: 1, clients: 0, clientsOutsideBbh: 0,
                                businessImpact: 'Low', changeComplexity: 'Low', validationComplexity: 'Low',
                                backoutTesting: 'Tested on QC, about 15 minutes', platformStatus: 'Existing platform']]

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

    static void install(StubApi api) {
        def profiles = new ConcurrentHashMap<Integer, Map>([1: [version: 2, template: CERT_TEMPLATE]])
        def changes = new CopyOnWriteArrayList<Map>([first()])
        def numbers = new AtomicInteger(31001)
        def projectOf = { RecordedRequest request, String id ->
            request.params().project ?: (profiles[id as int]?.template ?: suggested(id)).jiraProjectKey
        }

        api.get('/api/changes/integrations') { [jiraConnected: false, serviceNowConnected: false] }
        api.get('/api/changes') { changes.reverse() }
        api.get('/api/changes/(\\d+)') { RecordedRequest request, List<String> ids ->
            def found = changes.find { it.id == ids[0] as int }
            found ? json(found) : problem(404, 'Not found', "Change ${ids[0]} does not exist")
        }
        api.get('/api/change-profiles') {
            profiles.collect { id, stored ->
                [productId: id, productName: product(id).name, version: stored.version, updatedAt: '2026-10-05T12:00:00Z']
            }.sort { it.productName }
        }
        api.get('/api/products/(\\d+)/change-profile') { RecordedRequest request, List<String> ids ->
            def stored = profiles[ids[0] as int]
            [productId: ids[0] as int, productName: product(ids[0]).name, version: stored?.version,
             updatedAt: stored ? '2026-10-05T12:00:00Z' : null, template: stored?.template ?: suggested(ids[0])]
        }
        api.on('PUT', '/api/products/(\\d+)/change-profile') { RecordedRequest request, List<String> ids ->
            def stored = profiles[ids[0] as int]
            Map asked = request.json() as Map
            if (asked.version != stored?.version) {
                return problem(409, 'Conflict', "The ServiceNow defaults of ${product(ids[0]).name} were changed by someone else. Reload the page.")
            }
            def saved = [version: stored ? stored.version + 1 : 0, template: asked.template]
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
        api.on('POST', '/api/changes') { RecordedRequest request ->
            Map asked = request.json() as Map
            def number = "CHG00${numbers.incrementAndGet()}".toString()
            Map change = draft(asked) + [id: changes.size() + 1, number: number, createdAt: '2026-10-07T08:00:00Z',
                                         shortDescription: asked.shortDescription ?: draft(asked).shortDescription,
                                         description: asked.description ?: draft(asked).description]
            change.tasks = (change.tasks as List<Map>).withIndex().collect { task, index ->
                task + [number: String.format('CTASK%07d', (number.drop(3) as int) * 10 + index + 1)]
            }
            changes << change
            json(change, 201)
        }
    }

    static Map suggested(Object id) {
        def product = product(id)
        def letters = (product.code as String).replaceAll(/[^A-Z0-9]/, '')
        [jiraProjectKey  : letters.size() <= 6 ? letters : letters.take(4), assignmentGroup: product.ownerTeam ?: "$product.name Support",
         category        : 'Software', type: 'NORMAL', configurationItem: product.name, release: null, incident: null,
         problem         : null, affectedClients: null, description: product.description,
         approvers       : [l1Manager: null, l2Manager: null, businessApprover: null], downtime: false,
         timing          : [installationStart: '18:00', installationHours: 2, validationHours: 1], planning: PLANNING,
         privilegedAccess: [required: false, users: []],
         riskAssessment  : [bbhWorkgroups: null, bbhUsers: null, bbhApplications: null, clients: null, clientsOutsideBbh: null,
                            businessImpact: null, changeComplexity: null, validationComplexity: null, backoutTesting: null,
                            platformStatus: null]]
    }

    static Map draft(Map asked) {
        def product = product(asked.productId)
        def project = (asked.template as Map).jiraProjectKey as String
        def issues = issuesOf(project)
        def epics = issues.findAll { it.key in asked.epicKeys }
        def stories = issues.findAll { it.key in asked.storyKeys }
        def services = (product.services as List<Map>).findAll { !asked.serviceIds || it.id in asked.serviceIds }
        [id              : null, number: null, productId: product.id, productCode: product.code, productName: product.name,
         departmentName  : 'Corporate Technology', fixVersion: asked.fixVersion, schedule: asked.schedule,
         shortDescription: "$product.name $asked.fixVersion: ${epics*.summary.join('; ')}".toString(),
         description     : "Production release of $product.name ($product.code), FixVersion $asked.fixVersion.\n\nEpics:\n"
                 + epics.collect { "$it.key $it.summary ($it.status)" }.join('\n') + '\n\nStories:\n'
                 + stories.collect { "$it.key $it.summary" }.join('\n'),
         template        : asked.template + [release: (asked.template as Map).release ?: asked.fixVersion],
         epicKeys        : asked.epicKeys, storyKeys: asked.storyKeys,
         tasks           : services.collect { task(it.name as String, product.name as String) },
         url             : null, createdAt: null]
    }

    private static Map first() {
        def schedule = [installationStart: '2026-10-10T17:00:00Z', installationEnd: '2026-10-10T19:00:00Z',
                        validationStart  : '2026-10-10T19:00:00Z', validationEnd: '2026-10-10T20:00:00Z',
                        firstUsage       : '2026-10-12T08:00:00Z']
        def change = draft([productId: 1, serviceIds: [1], fixVersion: 'CERT 4.1', epicKeys: ['CERT-140'], storyKeys: [],
                            schedule : schedule, template: CERT_TEMPLATE])
        change + [id: 1, number: 'CHG0031001', createdAt: '2026-10-07T08:00:00Z',
                  tasks: (change.tasks as List<Map>).collect { it + [number: 'CTASK0310011'] }]
    }

    private static Map product(Object id) {
        fixture("product-${id}.json") as Map
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

    private static Map task(String service, String product) {
        [number     : null, serviceName: service, shortDescription: "Deploy $service of $product to production".toString(),
         description: "Deploy $service of $product, then run its smoke tests.".toString()]
    }

    private static Map issue(String key, String summary, String epicKey, String fixVersion, int daysAgo) {
        [key       : key, summary: summary, status: daysAgo < 10 ? 'In Review' : 'Done', epicKey: epicKey,
         fixVersion: fixVersion, updated: now().minusDays(daysAgo).toString()]
    }
}
