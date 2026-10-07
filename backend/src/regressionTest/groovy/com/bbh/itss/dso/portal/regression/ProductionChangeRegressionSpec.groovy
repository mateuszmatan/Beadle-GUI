package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.PortalSpecification

import java.time.Instant

import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ApiJson.service
import static com.bbh.itss.dso.portal.support.ChangeFixtures.scheduleJson
import static com.bbh.itss.dso.portal.support.ChangeFixtures.templateJson
import static java.net.URLEncoder.encode
import static java.nio.charset.StandardCharsets.UTF_8
import static java.time.temporal.ChronoUnit.DAYS
import static java.time.temporal.ChronoUnit.HOURS

class ProductionChangeRegressionSpec extends PortalSpecification {

    static final Instant START = Instant.now().plus(3, DAYS).truncatedTo(HOURS)
    static final List<String> CHANGE_KEYS = ['id', 'number', 'productId', 'productCode', 'productName',
                                             'departmentName', 'fixVersion', 'schedule', 'shortDescription',
                                             'description', 'template', 'epicKeys', 'storyKeys', 'tasks', 'url',
                                             'createdAt']
    static final List<String> TEMPLATE_KEYS = ['jiraProjectKey', 'assignmentGroup', 'category', 'type',
                                               'configurationItem', 'release', 'incident', 'problem',
                                               'affectedClients', 'description', 'approvers', 'downtime', 'timing',
                                               'planning', 'privilegedAccess', 'riskAssessment']

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
        suggested.json.keySet() as List == ['productId', 'productName', 'version', 'updatedAt', 'template']
        [suggested.json.productId, suggested.json.productName, suggested.json.version] == [created.id, created.name, null]
        suggested.json.template.keySet() as List == TEMPLATE_KEYS
        suggested.json.template.subMap('configurationItem', 'assignmentGroup', 'type', 'release', 'description',
                'approvers', 'downtime', 'timing', 'privilegedAccess', 'riskAssessment') ==
                [configurationItem: created.name, assignmentGroup: 'Ledger Ops', type: 'NORMAL', release: null,
                 description: 'Posts the ledger.', approvers: [l1Manager: null, l2Manager: null, businessApprover: null],
                 downtime: false, timing: [installationStart: '18:00', installationHours: 2, validationHours: 1],
                 privilegedAccess: [required: false, users: []],
                 riskAssessment: [bbhWorkgroups: null, bbhUsers: null, bbhApplications: null, clients: null,
                                  clientsOutsideBbh: null, businessImpact: null, changeComplexity: null,
                                  validationComplexity: null, backoutTesting: null, platformStatus: null]]
        suggested.json.template.planning.keySet() as List ==
                ['testSummary', 'implementationPlan', 'validationPlan', 'backoutPlan', 'firstUsePlan']

        when:
        def saved = api.put("/api/products/$created.id/change-profile", [version: null, template: templateJson()])
        def changed = api.put("/api/products/$created.id/change-profile", [version: 0, template: privileged])
        def stale = api.put("/api/products/$created.id/change-profile", [version: 0, template: templateJson()])

        then:
        saved.status == 200
        saved.json.version == 0
        saved.json.template == templateJson()
        changed.json.version == 1
        changed.json.template == templateJson('privilegedAccess.required': true,
                'privilegedAccess.users': [[user: 'Jane Smith', account: 'adm_jsmith'], [user: 'Ann Lee', account: 'adm_alee']],
                'approvers.l1Manager': 'Emma Brooks', jiraProjectKey: 'LEDG', incident: 'INC0012345')
        stale.status == 409
        api.get("/api/products/$created.id/change-profile").json == changed.json
        api.get('/api/change-profiles').json.find { it.productId == created.id } ==
                [productId: created.id, productName: created.name, version: 1, updatedAt: changed.json.updatedAt]
    }

    def "a template with #problem is refused against its field"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Product ${uniqueCode()}"))

        when:
        def response = api.put("/api/products/$created.id/change-profile", [template: templateJson(edits)])

        then:
        response.status == 400
        response.json.errors*.field == [field]
        api.get("/api/products/$created.id/change-profile").json.version == null

        where:
        problem                          | edits                                                          || field
        'a Jira key with a dash'         | [jiraProjectKey: 'ce-rt']                                      || 'template.jiraProjectKey'
        'no assignment group'            | [assignmentGroup: ' ']                                         || 'template.assignmentGroup'
        'an unknown type'                | [type: 'MINOR']                                                || 'template.type'
        'no backout plan'                | ['planning.backoutPlan': ' ']                                  || 'template.planning.backoutPlan'
        'a long first use plan'          | ['planning.firstUsePlan': 'x' * 2001]                          || 'template.planning.firstUsePlan'
        'no installation start'          | ['timing.installationStart': '6pm']                            || 'template.timing.installationStart'
        'an installation of four days'   | ['timing.installationHours': 96]                               || 'template.timing.installationHours'
        'a negative number of users'     | ['riskAssessment.bbhUsers': -1]                                || 'template.riskAssessment.bbhUsers'
        'a long platform status'         | ['riskAssessment.platformStatus': 'p' * 101]                   || 'template.riskAssessment.platformStatus'
        'a privileged user without account' | ['privilegedAccess.required': true, 'privilegedAccess.users': [[user: 'A', account: 'a'], [user: 'B', account: 'b'], [user: 'C', account: ' ']]] || 'template.privilegedAccess.users[2].account'
        'privileged access without users' | ['privilegedAccess.required': true]                           || 'template.privilegedAccess.users'
        'users without privileged access' | ['privilegedAccess.users': [[user: 'A', account: 'a']]]       || 'template.privilegedAccess.users'
        'eight privileged users'         | ['privilegedAccess.required': true, 'privilegedAccess.users': (1..8).collect { [user: "U$it", account: "u$it"] }] || 'template.privilegedAccess.users'
        'no planning'                    | [planning: null]                                               || 'template.planning'
    }

    def "the stored change profiles are listed by product name, without products that have none"() {
        given:
        def zeta = createProduct(product(code: uniqueCode(), name: "Zeta ${uniqueCode()}"))
        def alpha = createProduct(product(code: uniqueCode(), name: "Alpha ${uniqueCode()}"))
        def none = createProduct(product(code: uniqueCode(), name: "Beta ${uniqueCode()}"))
        api.put("/api/products/$zeta.id/change-profile", [template: templateJson()])
        api.put("/api/products/$alpha.id/change-profile", [template: templateJson()])

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
        api.put("/api/products/$created.id/change-profile", [template: templateJson(jiraProjectKey: 'STORED')])
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

    def "a change is drafted from the epics and stories of a FixVersion, raised with a task per service and listed"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Payments ${uniqueCode()}",
                services: [service(name: 'gui'), service(name: 'api'), service(name: 'batch')]))
        def key = 'PAY' + created.id
        api.put("/api/products/$created.id/change-profile", [template: templateJson(jiraProjectKey: key)])

        when:
        def versions = api.get("/api/products/$created.id/jira/versions").json
        def fixVersion = versions[0].name
        def epics = api.get("/api/products/$created.id/jira/epics?fixVersion=${enc(fixVersion)}").json
        def chosen = epics.take(2)*.key
        def stories = api.get("/api/products/$created.id/jira/stories?fixVersion=${enc(fixVersion)}" +
                "&epics=${chosen.join(',')}").json
        def services = created.services.findAll { it.name != 'batch' }*.id
        def template = templateJson(jiraProjectKey: key, 'riskAssessment.businessImpact': 'Medium')
        def preview = api.post('/api/changes/preview', change(created, fixVersion, services.reverse(), chosen,
                stories*.key, [template: template]))

        then:
        fixVersion.startsWith(key + ' ')
        !versions[0].released
        epics.size() >= 2
        !stories.isEmpty()
        stories.every { it.epicKey in chosen }
        preview.status == 200
        preview.json.keySet() as List == CHANGE_KEYS
        [preview.json.id, preview.json.number, preview.json.url, preview.json.createdAt] == [null] * 4
        preview.json.fixVersion == fixVersion
        preview.json.schedule == scheduleJson(START)
        preview.json.template == template + [release: fixVersion]
        preview.json.shortDescription == "$created.name $fixVersion: ${epics.take(2)*.summary.join('; ')}"
        preview.json.description.startsWith("Production release $fixVersion of $created.name ($created.code) in Corporate Technology.\n")
        preview.json.description.contains("Scope from Jira project $key, FixVersion $fixVersion:\n${epics[0].key} ${epics[0].summary}")
        stories.every { preview.json.description.contains("- $it.key $it.summary") }
        preview.json.description.contains('Backout plan:\nRedeploy the previous release.\n')
        preview.json.description.contains('Privileged access: not needed.')
        preview.json.description.contains('Business impact: Medium')
        preview.json.tasks*.serviceName == ['gui', 'api']

        when:
        def raised = api.post('/api/changes', change(created, fixVersion, services, chosen, stories*.key,
                [shortDescription: ' Payments release 42 ', description: preview.json.description + '\nExtra.',
                 template: templateJson(jiraProjectKey: key, release: 'Payments 42')]))

        then:
        raised.status == 201
        raised.header('Location') == "$api.baseUrl/api/changes/${raised.json.id}"
        raised.json.number ==~ /CHG\d{7}/
        raised.json.tasks*.number.every { it ==~ /CTASK\d{7}/ }
        raised.json.shortDescription == 'Payments release 42'
        raised.json.description.endsWith('\nExtra.')
        raised.json.fixVersion == fixVersion
        raised.json.schedule == scheduleJson(START)
        raised.json.template.release == 'Payments 42'
        raised.json.epicKeys == chosen
        raised.json.storyKeys == stories*.key
        raised.json.createdAt != null
        raised.json.keySet() as List == CHANGE_KEYS
        api.get("/api/changes/$raised.json.id").json == raised.json
        api.get('/api/changes').json[0] == raised.json
    }

    def "a product without stored ServiceNow defaults raises a change with the template it sends"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Fresh ${uniqueCode()}"))
        def template = templateJson(jiraProjectKey: 'FRESH', 'privilegedAccess.required': true,
                'privilegedAccess.users': [[user: 'Jane Smith', account: 'adm_jsmith']])
        def fixVersion = api.get("/api/products/$created.id/jira/versions?project=FRESH").json[0].name
        def epic = api.get("/api/products/$created.id/jira/epics?fixVersion=${enc(fixVersion)}&project=FRESH").json[0]

        when:
        def raised = api.post('/api/changes', change(created, fixVersion, [], [epic.key], [], [template: template]))

        then:
        raised.status == 201
        raised.json.template == template + [release: fixVersion]
        raised.json.tasks*.serviceName == created.services*.name
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
        def response = api.post('/api/changes', change(created, fixVersion, [created.services[0].id], [epic], [], body))

        then:
        response.status == 400
        response.json.errors*.field == fields
        api.get('/api/changes').json.every { it.productId != created.id }

        where:
        refusal                       | edits                                                          || fields
        'no FixVersion'               | [fixVersion: ' ']                                              || ['fixVersion']
        'a service of another product' | [serviceIds: [999999]]                                        || ['serviceIds']
        'an installation in the past' | [schedule: scheduleJson(Instant.parse('2020-01-01T08:00:00Z'))] || ['schedule.installationStart']
        'an installation ending first' | [schedule: scheduleJson(START) + [installationEnd: START.toString()]] || ['schedule.installationEnd']
        'a validation before the end' | [schedule: scheduleJson(START) + [validationStart: START.toString()]] || ['schedule.validationStart']
        'no first usage'              | [schedule: scheduleJson(START) + [firstUsage: null]]           || ['schedule.firstUsage']
        'no schedule'                 | [schedule: null]                                               || ['schedule']
        'no template'                 | [template: null]                                               || ['template']
        'no backout plan'             | [template: templateJson('planning.backoutPlan': '')]           || ['template.planning.backoutPlan']
        'privileged access without users' | [template: templateJson('privilegedAccess.required': true)] || ['template.privilegedAccess.users']
        'no epics'                    | [epicKeys: []]                                                 || ['epicKeys']
        'an unknown epic'             | [epicKeys: ['NOPE-1']]                                         || ['epicKeys']
        'a too long short text'       | [shortDescription: 's' * 161]                                  || ['shortDescription']
    }

    def "the preview accepts an installation start in the past, raising it does not"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Product ${uniqueCode()}"))
        def key = 'PRE' + created.id
        def fixVersion = api.get("/api/products/$created.id/jira/versions?project=$key").json[0].name
        def epic = api.get("/api/products/$created.id/jira/epics?fixVersion=${enc(fixVersion)}&project=$key")
                .json[0].key
        def past = change(created, fixVersion, [], [epic], [], [template: templateJson(jiraProjectKey: key),
                                                               schedule: scheduleJson(Instant.parse('2026-01-05T17:00:00Z'))])

        expect:
        api.post('/api/changes/preview', past).json.schedule.installationStart == '2026-01-05T17:00:00Z'
        api.post('/api/changes', past).json.errors == [[field: 'schedule.installationStart', message: 'must be in the future']]
    }

    def "a change outlives its product and Jira refuses requests without a FixVersion or with a broken project"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Product ${uniqueCode()}"))
        def key = 'OLD' + created.id
        def fixVersion = api.get("/api/products/$created.id/jira/versions?project=$key").json[0].name
        def epic = api.get("/api/products/$created.id/jira/epics?fixVersion=${enc(fixVersion)}&project=$key")
                .json[0].key
        api.put("/api/products/$created.id/change-profile", [template: templateJson(jiraProjectKey: key)])
        def raised = api.post('/api/changes', change(created, fixVersion, [], [epic], [],
                [template: templateJson(jiraProjectKey: key)])).json

        when:
        def deleted = api.delete("/api/products/$created.id")
        def kept = api.get("/api/changes/$raised.id").json

        then:
        deleted.status == 204
        kept.productId == null
        kept.productName == created.name
        kept.fixVersion == fixVersion
        kept.tasks*.number == raised.tasks*.number
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_CHANGE_PROFILE WHERE PRODUCT_ID = ?', Integer, created.id) == 0
        api.get("/api/products/$created.id/jira/versions").status == 404

        and:
        api.get('/api/products/1/jira/epics').json.errors*.field == ['fixVersion']
        api.get('/api/products/1/jira/epics?fixVersion=%20').json.errors*.field == ['fixVersion']
        api.get('/api/products/1/jira/stories?epics=CERT-1&fixVersion=').json.errors*.field == ['fixVersion']
        api.get('/api/products/1/jira/versions?project=ce-rt').json.errors*.field == ['project']
        api.get('/api/changes/99999').status == 404
        api.get('/api/changes/integrations').json == [jiraConnected: false, serviceNowConnected: false]
    }

    private static Map change(Map product, String fixVersion, List serviceIds, List epicKeys, List storyKeys = [],
                              Map edits = [:]) {
        [productId: product.id, serviceIds: serviceIds, fixVersion: fixVersion, epicKeys: epicKeys,
         storyKeys: storyKeys, schedule: scheduleJson(START), template: templateJson()] + edits
    }

    private static String enc(String text) {
        encode(text, UTF_8).replace('+', '%20')
    }
}
