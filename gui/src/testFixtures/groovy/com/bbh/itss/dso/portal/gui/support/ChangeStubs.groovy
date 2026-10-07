package com.bbh.itss.dso.portal.gui.support

import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

final class ChangeStubs {

    static final Map CERT_TEMPLATE = [
            jiraProjectKey    : 'CERT', configurationItem: 'CertScanner', assignmentGroup: 'Technology Architecture',
            type              : 'NORMAL', category: 'Software', risk: 'MODERATE', impact: 'LOW',
            riskAssessment    : 'Routine release of tested changes, rolled back within minutes.',
            approvers         : ['Olivia Bennett', 'James Carter'], description: 'Scans TLS certificates.',
            implementationPlan: 'Deploy each service with its change task.',
            backoutPlan       : 'Redeploy the previous release.', testPlan: 'Pipeline tests passed on QC.']

    static final List<Map> CERT_ISSUES = [
            issue('CERT-120', 'Expiry alerts for certificates', null, 3),
            issue('CERT-121', 'E-mail the certificate owner', 'CERT-120', 5),
            issue('CERT-122', 'Teams alert on expiry', 'CERT-120', 8),
            issue('CERT-130', 'Audit trail of user actions', null, 12),
            issue('CERT-131', 'Record each change with its user', 'CERT-130', 14),
            issue('CERT-140', 'Upgrade to Java 21', null, 40)]

    private ChangeStubs() {
    }

    static void install(StubApi api) {
        def profiles = new ConcurrentHashMap<Integer, Map>([1: [version: 2, template: CERT_TEMPLATE]])
        def changes = new CopyOnWriteArrayList<Map>([raised(1, 'CHG0031001', [1], ['CERT-120'], ['CERT-121'],
                'CertScanner release: Expiry alerts for certificates', 'Production release of CertScanner.')])
        def numbers = new AtomicInteger(31001)

        api.get('/api/changes/integrations') { [jiraConnected: false, serviceNowConnected: false] }
        api.get('/api/changes') { changes.reverse() }
        api.get('/api/changes/(\\d+)') { RecordedRequest request, List<String> ids ->
            def found = changes.find { it.id == ids[0] as int }
            found ? StubResponse.json(found) : StubResponse.problem(404, 'Not found', "Change ${ids[0]} does not exist")
        }
        api.get('/api/products/(\\d+)/change-profile') { RecordedRequest request, List<String> ids ->
            def product = StubApi.fixture("product-${ids[0]}.json") as Map
            def stored = profiles[ids[0] as int]
            [productId: product.id, productName: product.name, version: stored?.version,
             updatedAt: stored ? '2026-10-05T12:00:00Z' : null,
             template : stored?.template ?: CERT_TEMPLATE + [jiraProjectKey: product.code.take(4),
                                                             configurationItem: product.name, riskAssessment: null,
                                                             approvers: [], description: product.description]]
        }
        api.on('PUT', '/api/products/(\\d+)/change-profile') { RecordedRequest request, List<String> ids ->
            def stored = profiles[ids[0] as int]
            def saved = [version: stored ? stored.version + 1 : 0, template: request.json().template]
            profiles[ids[0] as int] = saved
            def product = StubApi.fixture("product-${ids[0]}.json") as Map
            [productId: product.id, productName: product.name, updatedAt: '2026-10-07T09:00:00Z'] + saved
        }
        api.get('/api/products/(\\d+)/jira/epics') { CERT_ISSUES.findAll { it.epicKey == null } }
        api.get('/api/products/(\\d+)/jira/stories') { RecordedRequest request ->
            def epics = request.params().epics?.split(',') as List ?: []
            CERT_ISSUES.findAll { it.epicKey in epics }
        }
        api.on('POST', '/api/changes/preview') { RecordedRequest request -> draft(request.json() as Map) }
        api.on('POST', '/api/changes') { RecordedRequest request ->
            Map asked = request.json() as Map
            Map numbered = raised(changes.size() + 1, "CHG00${numbers.incrementAndGet()}", asked.serviceIds as List,
                    asked.epicKeys as List, asked.storyKeys as List, asked.shortDescription as String,
                    asked.description as String)
            Map change = draft(asked) + numbered.subMap('id', 'number', 'shortDescription', 'description', 'tasks',
                    'createdAt')
            changes << change
            StubResponse.json(change, 201)
        }
    }

    static Map draft(Map asked) {
        def product = StubApi.fixture("product-${asked.productId}.json") as Map
        def epics = CERT_ISSUES.findAll { it.key in asked.epicKeys }
        def services = (product.services as List<Map>).findAll { it.id in asked.serviceIds }
        [id              : null, number: null, productId: product.id, productCode: product.code,
         productName     : product.name, departmentName: 'Corporate Technology',
         window          : [start: asked.start, end: asked.end],
         shortDescription: "$product.name release: ${epics*.summary.join('; ')}".toString(),
         description     : "Production release of $product.name ($product.code).\n\nScope from Jira project CERT:\n"
                 + epics.collect { "$it.key $it.summary ($it.status)" }.join('\n'),
         template        : CERT_TEMPLATE, epicKeys: asked.epicKeys, storyKeys: asked.storyKeys,
         tasks           : services.collect { task(null, it.name as String, product.name as String) },
         url             : null, createdAt: null]
    }

    private static Map raised(int id, String number, List serviceIds, List epicKeys, List storyKeys,
                              String shortDescription, String description) {
        def product = StubApi.fixture('product-1.json') as Map
        def names = (product.services as List<Map>).findAll { it.id in serviceIds }*.name
        [id         : id, number: number, productId: 1, productCode: product.code, productName: product.name,
         departmentName: 'Corporate Technology',
         window     : [start: '2026-10-10T06:00:00Z', end: '2026-10-10T10:00:00Z'],
         shortDescription: shortDescription, description: description, template: CERT_TEMPLATE,
         epicKeys   : epicKeys, storyKeys: storyKeys,
         tasks      : names.withIndex().collect { name, index ->
             task(String.format('CTASK%07d', (number.drop(3) as int) * 10 + index + 1), name as String,
                     product.name as String)
         }, url: null, createdAt: '2026-10-07T08:00:00Z']
    }

    private static Map task(String number, String service, String product) {
        [number     : number, serviceName: service, shortDescription: "Deploy $service of $product to production".toString(),
         description: "Deploy $service of $product, then run its smoke tests.".toString()]
    }

    private static Map issue(String key, String summary, String epicKey, int daysAgo) {
        [key    : key, summary: summary, status: daysAgo < 10 ? 'In Review' : 'Done', epicKey: epicKey,
         updated: LocalDate.now().minusDays(daysAgo).toString()]
    }
}
