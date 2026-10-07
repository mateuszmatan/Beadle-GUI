package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.PortalSpecification

import java.time.Instant
import java.time.LocalDate

import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ApiJson.service
import static com.bbh.itss.dso.portal.support.ChangeFixtures.templateJson
import static java.time.temporal.ChronoUnit.DAYS
import static java.time.temporal.ChronoUnit.HOURS

class ProductionChangeRegressionSpec extends PortalSpecification {

    static final String START = Instant.now().plus(3, DAYS).truncatedTo(HOURS).toString()
    static final String END = Instant.parse(START).plus(4, HOURS).toString()
    static final String FROM = LocalDate.now().minusDays(300).toString()
    static final String TO = LocalDate.now().toString()

    def "a product first gets a suggested template, then saves and changes its own at the version it read"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Ledger ${uniqueCode()}", ownerTeam: 'Ledger Ops',
                description: 'Posts the ledger.'))

        when:
        def suggested = api.get("/api/products/$created.id/change-profile")

        then:
        suggested.status == 200
        suggested.json.keySet() as List == ['productId', 'productName', 'version', 'updatedAt', 'template']
        [suggested.json.productId, suggested.json.productName, suggested.json.version] == [created.id, created.name, null]
        suggested.json.template.subMap('configurationItem', 'assignmentGroup', 'type', 'risk', 'approvers',
                'description') == [configurationItem: created.name, assignmentGroup: 'Ledger Ops', type: 'NORMAL',
                                   risk: 'LOW', approvers: [], description: 'Posts the ledger.']

        when:
        def saved = api.put("/api/products/$created.id/change-profile", [version: null, template: templateJson()])
        def changed = api.put("/api/products/$created.id/change-profile",
                [version: 0, template: templateJson(risk: 'HIGH', approvers: [' Emma Brooks ', 'Emma Brooks'])])
        def stale = api.put("/api/products/$created.id/change-profile", [version: 0, template: templateJson()])

        then:
        saved.status == 200
        saved.json.version == 0
        saved.json.template == templateJson()
        changed.json.version == 1
        changed.json.template == templateJson(risk: 'HIGH', approvers: ['Emma Brooks'])
        stale.status == 409
        api.get("/api/products/$created.id/change-profile").json == changed.json
    }

    def "a template with #problem is refused against its field"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Product ${uniqueCode()}"))

        when:
        def response = api.put("/api/products/$created.id/change-profile", [template: templateJson(edits)])

        then:
        response.status == 400
        response.json.errors*.field == [field]

        where:
        problem                  | edits                                || field
        'a Jira key with a dash' | [jiraProjectKey: 'ce-rt']            || 'template.jiraProjectKey'
        'no approvers'           | [approvers: []]                      || 'template.approvers'
        'no risk assessment'     | [riskAssessment: ' ']                || 'template.riskAssessment'
        'an unknown risk'        | [risk: null]                         || 'template.risk'
        'a long backout plan'    | [backoutPlan: 'x' * 2001]            || 'template.backoutPlan'
    }

    def "a product without a template cannot read Jira or raise a change yet"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Product ${uniqueCode()}"))

        when:
        def epics = api.get("/api/products/$created.id/jira/epics?from=$FROM&to=$TO")
        def preview = api.post('/api/changes/preview', change(created, [created.services[0].id], ['X-1']))

        then:
        [epics, preview]*.status == [409, 409]
        epics.json.detail == "$created.name has no ServiceNow change template yet. Fill it in under DevSecOps Product Management first."
    }

    def "a change is drafted from the chosen Jira epics and stories, raised with a task per service and listed"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Payments ${uniqueCode()}",
                services: [service(name: 'gui'), service(name: 'api'), service(name: 'batch')]))
        def key = 'PAY' + created.id
        api.put("/api/products/$created.id/change-profile", [template: templateJson(jiraProjectKey: key)])

        when:
        def epics = api.get("/api/products/$created.id/jira/epics?from=$FROM&to=$TO").json
        def chosen = epics.take(2)*.key
        def stories = api.get("/api/products/$created.id/jira/stories?epics=${chosen.join(',')}&from=$FROM&to=$TO").json
        def services = created.services.findAll { it.name != 'batch' }*.id
        def preview = api.post('/api/changes/preview', change(created, services.reverse(), chosen, stories*.key))

        then:
        epics.size() == 8
        epics.every { it.key.startsWith(key + '-') && it.epicKey == null }
        epics[0].keySet() as List == ['key', 'summary', 'status', 'epicKey', 'updated']
        !stories.isEmpty()
        stories.every { it.epicKey in chosen }
        preview.status == 200
        preview.json.id == null
        preview.json.number == null
        preview.json.shortDescription == "$created.name release: ${epics.take(2)*.summary.join('; ')}"
        preview.json.description.startsWith("Production release of $created.name ($created.code) in Corporate Technology")
        preview.json.description.contains("Scope from Jira project $key:\n${epics[0].key} ${epics[0].summary}")
        stories.every { preview.json.description.contains("- $it.key $it.summary") }
        preview.json.tasks*.serviceName == ['gui', 'api']
        preview.json.template.jiraProjectKey == key

        when:
        def raised = api.post('/api/changes', change(created, services, chosen, stories*.key,
                [shortDescription: ' Payments release 42 ', description: preview.json.description + '\nExtra.']))

        then:
        raised.status == 201
        raised.header('Location') == "$api.baseUrl/api/changes/${raised.json.id}"
        raised.json.number ==~ /CHG\d{7}/
        raised.json.tasks*.number.every { it ==~ /CTASK\d{7}/ }
        raised.json.shortDescription == 'Payments release 42'
        raised.json.description.endsWith('\nExtra.')
        raised.json.window == [start: START, end: END]
        raised.json.epicKeys == chosen
        raised.json.storyKeys == stories*.key
        raised.json.createdAt != null
        raised.json.keySet() as List == ['id', 'number', 'productId', 'productCode', 'productName', 'departmentName',
                                         'window', 'shortDescription', 'description', 'template', 'epicKeys',
                                         'storyKeys', 'tasks', 'url', 'createdAt']
        api.get("/api/changes/$raised.json.id").json == raised.json
        api.get('/api/changes').json[0] == raised.json
    }

    def "a change of #refusal is refused with the field to fix"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Product ${uniqueCode()}"))
        api.put("/api/products/$created.id/change-profile", [template: templateJson(jiraProjectKey: 'REG' + created.id)])
        def epic = api.get("/api/products/$created.id/jira/epics?from=$FROM&to=$TO").json[0].key

        when:
        def response = api.post('/api/changes', change(created, [created.services[0].id], [epic], [], edits))

        then:
        response.status == 400
        response.json.errors*.field == fields
        api.get('/api/changes').json.every { it.productId != created.id }

        where:
        refusal                    | edits                                                      || fields
        'no services'              | [serviceIds: []]                                           || ['serviceIds']
        'a window in the past'     | [start: '2020-01-01T08:00:00Z', end: '2020-01-01T10:00:00Z'] || ['start']
        'a window ending first'    | [end: START]                                               || ['end']
        'no epics'                 | [epicKeys: []]                                             || ['epicKeys']
        'an unknown epic'          | [epicKeys: ['NOPE-1']]                                     || ['epicKeys']
        'a too long short text'    | [shortDescription: 's' * 161]                              || ['shortDescription']
    }

    def "a change outlives its product and Jira reads only valid date ranges"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Product ${uniqueCode()}"))
        api.put("/api/products/$created.id/change-profile", [template: templateJson(jiraProjectKey: 'OLD' + created.id)])
        def epic = api.get("/api/products/$created.id/jira/epics?from=$FROM&to=$TO").json[0].key
        def raised = api.post('/api/changes', change(created, [created.services[0].id], [epic])).json

        when:
        def deleted = api.delete("/api/products/$created.id")
        def kept = api.get("/api/changes/$raised.id").json

        then:
        deleted.status == 204
        kept.productId == null
        kept.productName == created.name
        kept.tasks*.number == raised.tasks*.number
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_CHANGE_PROFILE WHERE PRODUCT_ID = ?', Integer, created.id) == 0

        and:
        api.get("/api/products/1/jira/epics?from=$TO&to=$FROM").json.errors*.field == ['to']
        api.get('/api/changes/99999').status == 404
        api.get('/api/changes/integrations').json == [jiraConnected: false, serviceNowConnected: false]
    }

    private static Map change(Map product, List serviceIds, List epicKeys, List storyKeys = [], Map edits = [:]) {
        [productId: product.id, serviceIds: serviceIds, epicKeys: epicKeys, storyKeys: storyKeys, start: START,
         end      : END] + edits
    }
}
