package com.bbh.itss.beadle.regression

import com.bbh.itss.beadle.support.BeadleClient
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

import java.net.CookieManager
import java.time.Instant

import static com.bbh.itss.beadle.support.ChangeFixtures.secureCodingJson
import static com.bbh.itss.beadle.support.ChangeFixtures.tasksJson
import static com.bbh.itss.beadle.support.ChangeFixtures.templateJson
import static java.nio.charset.StandardCharsets.UTF_8

class AuditTrailRegressionSpec extends ChangeRegressionSpecification {

    static final String CORRELATION = '0b7c2f4e-9d1a-4e6b-8c3f-5a2d7e9f1b40'
    static final int DETAILS_MAX = 32 * 1024
    static final JsonMapper STRICT = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build()

    def "creating a product leaves one SUCCESS row under the correlation id of the request"() {
        given:
        def code = uniqueCode()

        when:
        def response = api.post('/api/products', product(code: code, name: "Audited ${code}"))

        then:
        response.status == 201
        rowsAbout(code) == [[USER_NAME: SIGNED_IN, ACTION: 'PRODUCT_CREATED', OBJECT_TYPE: 'PRODUCT',
                             OBJECT_ID: response.json.id as Long, OUTCOME: 'SUCCESS', MESSAGE: null,
                             CORRELATION_ID: response.header('X-Correlation-Id')]]
        details(code)[0].contains("\"code\":\"${code}\"")
    }

    def "a user cannot file an action under a correlation id of their choosing"() {
        given:
        def code = uniqueCode()

        when:
        def response = api.post('/api/products', product(code: code, name: "Chosen ${code}"),
                ['X-Correlation-Id': CORRELATION])

        then:
        response.status == 201
        response.header('X-Correlation-Id') != CORRELATION
        rowsAbout(code)*.CORRELATION_ID == [response.header('X-Correlation-Id')]
    }

    def "a refused product is rolled back, but its FAILURE row stays"() {
        given:
        def code = uniqueCode()
        createProduct(product(code: code, name: "First ${code}"))

        when:
        def refused = api.post('/api/products', product(code: code, name: "Second ${code}"))

        then:
        refused.status == 409
        jdbc.queryForObject('SELECT COUNT(*) FROM BEADLE_PRODUCT WHERE CODE = ?', Integer, code) == 1
        rowsAbout(code)*.subMap(['ACTION', 'OUTCOME', 'CORRELATION_ID']) == [
                [ACTION: 'PRODUCT_CREATED', OUTCOME: 'SUCCESS', CORRELATION_ID: rowsAbout(code)[0].CORRELATION_ID],
                [ACTION: 'PRODUCT_CREATED', OUTCOME: 'FAILURE', CORRELATION_ID: refused.header('X-Correlation-Id')]]
        rowsAbout(code)[1].MESSAGE == refused.json.detail
    }

    def "raising a change and creating its tasks is filed under the change number"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Raised ${uniqueCode()}"))

        when:
        def raised = raise(created)

        then:
        rowsAbout(raised.number as String)*.ACTION == ['CHANGE_RAISED', 'CHANGE_TASKS_CREATED']
        rowsAbout(raised.number as String).every { it.OUTCOME == 'SUCCESS' && it.OBJECT_ID == raised.id as Long }
    }

    def "searching the trail for a CTASK or a ticket key finds the row of the action that created it"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Created ${uniqueCode()}")))

        when:
        def ticket = api.post("/api/changes/$raised.id/secure-coding", [version: raised.version,
                departmentId: raised.departmentId, implementationDate: '10202026'] + secureCodingJson())

        then:
        ticket.status == 200
        def key = ticket.json.template.secureCodingTicket as String
        key ==~ /SCP-\d+/
        raised.tasks*.number.every { String number ->
            def found = api.get("/api/action-history?objectKey=$number").json.content as List<Map>
            found*.action == ['CHANGE_TASKS_CREATED'] && found[0].objectKey == raised.number &&
                    (found[0].details as String).endsWith(',"result":{"created":["' +
                            raised.tasks*.number.join('","') + '"]}}')
        }
        def ticketRows = api.get("/api/action-history?objectKey=$key").json.content as List<Map>
        ticketRows*.action == ['SECURE_CODING_TICKET_CREATED']
        ticketRows[0].subMap(['user', 'objectType', 'objectId', 'objectKey']) ==
                [user: SIGNED_IN, objectType: 'CHANGE', objectId: raised.id, objectKey: raised.number]
        (ticketRows[0].details as String).endsWith(',"result":{"created":["' + key + '"]}}')
        def raiseRows = api.get("/api/action-history?objectKey=$raised.number&action=CHANGE_RAISED").json.content
        raiseRows.size() == 1
        (raiseRows[0].details as String).endsWith(',"result":{"created":["' + raised.number + '"]}}')
    }

    def "a department that could not be created is filed under the name it was to have"() {
        given:
        def name = "Audited ${uniqueCode()}"
        assert api.post('/api/departments', [name: name]).status == 201

        when:
        def refused = api.post('/api/departments', [name: " ${name} ".toString()])

        then:
        refused.status == 409
        rowsAbout(name)*.subMap(['ACTION', 'OBJECT_TYPE', 'OUTCOME']) == [
                [ACTION: 'DEPARTMENT_CREATED', OBJECT_TYPE: 'DEPARTMENT', OUTCOME: 'SUCCESS'],
                [ACTION: 'DEPARTMENT_CREATED', OBJECT_TYPE: 'DEPARTMENT', OUTCOME: 'FAILURE']]
    }

    def "a raise refused by the validation of its request leaves a FAILURE row filed under its product"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Refused ${code}"))

        when:
        def blank = api.post('/api/changes', change(created, '', ['CHG-1']))
        def bare = api.post('/api/changes', [productId: created.id])

        then:
        blank.status == 400
        blank.json.problems == [[field: 'fixVersion', message: 'must not be blank']]
        rowOf(blank) == [USER_NAME: SIGNED_IN, ACTION: 'CHANGE_RAISED', OBJECT_TYPE: 'PRODUCT',
                         OBJECT_ID: created.id as Long, OBJECT_KEY: code, OUTCOME: 'FAILURE',
                         MESSAGE: 'fixVersion must not be blank', DURATION_MS: 0L]
        detailsOf(blank).startsWith("""{"request":{"productId":$created.id,"fixVersion":"",""".toString())

        and:
        bare.status == 400
        rowOf(bare).subMap(['ACTION', 'OBJECT_KEY', 'OUTCOME']) ==
                [ACTION: 'CHANGE_RAISED', OBJECT_KEY: code, OUTCOME: 'FAILURE']
        (rowOf(bare).MESSAGE as String).split('; ')*.split(' ')*.getAt(0) as Set ==
                (bare.json.problems*.field as Set)
        detailsOf(bare) == """{"request":{"productId":$created.id}}""".toString()
    }

    def "a template of 12 long tasks and a refused body of 100 KB leave details that are JSON within 32 KB"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Large ${code}"))
        def tasks = (1..12).collect { tasksJson(1)[0] + [description: 'Step ' + it + ' ' + 'x' * 3_890] }
        def large = change(created, 'CERT 4.2', ['CHG-1']) + [description: 'T' * 100_000]

        when:
        def saved = api.put("/api/products/$created.id/change-profile", [template: templateJson(), tasks: tasks])
        def preview = api.post('/api/changes/preview', large)
        def raise = api.post('/api/changes', large + [fixVersion: ''])

        then:
        saved.status == 200
        rowOf(saved).subMap(['ACTION', 'OBJECT_ID', 'OUTCOME']) ==
                [ACTION: 'CHANGE_PROFILE_UPDATED', OBJECT_ID: created.id as Long, OUTCOME: 'SUCCESS']
        def template = parsed(detailsOf(saved))
        template.get('productId').asLong() == created.id as Long
        template.get('tasks').size() == 12
        template.get('tasks').every { it.get('description').stringValue() ==~ /Step \d+ x+…/ }

        and:
        [preview, raise]*.status == [400, 400]
        rowOf(preview).subMap(['ACTION', 'OUTCOME', 'MESSAGE']) ==
                [ACTION: 'CHANGE_PREVIEWED', OUTCOME: 'FAILURE', MESSAGE: 'description size must be between 0 and 4000']
        rowOf(raise).subMap(['ACTION', 'OUTCOME']) == [ACTION: 'CHANGE_RAISED', OUTCOME: 'FAILURE']
        [preview, raise].every { response ->
            def request = parsed(detailsOf(response)).get('request')
            request.get('productId').asLong() == created.id as Long &&
                    request.get('description').stringValue() ==~ /T{30000,}…/ &&
                    request.get('fixVersion').stringValue() == (response.is(raise) ? '' : 'CERT 4.2')
        }
    }

    def "a department refused by the validation of its name leaves a FAILURE row with the field problem"() {
        when:
        def blank = api.post('/api/departments', [name: ''])
        def tooLong = api.post('/api/departments', [name: 'X' * 300])

        then:
        [blank, tooLong]*.status == [400, 400]
        rowOf(blank) == [USER_NAME: SIGNED_IN, ACTION: 'DEPARTMENT_CREATED', OBJECT_TYPE: 'DEPARTMENT', OBJECT_ID: null,
                         OBJECT_KEY: null, OUTCOME: 'FAILURE', MESSAGE: 'name must not be blank', DURATION_MS: 0L]
        detailsOf(blank) == '{"request":{"name":""}}'
        rowOf(tooLong).subMap(['ACTION', 'OUTCOME', 'MESSAGE']) ==
                [ACTION: 'DEPARTMENT_CREATED', OUTCOME: 'FAILURE', MESSAGE: 'name size must be between 0 and 100']
        (rowOf(tooLong).OBJECT_KEY as String).startsWith('XXXXXXXXXX')
        api.get("/api/action-history?action=DEPARTMENT_CREATED&outcome=FAILURE&size=200").json.content
                .findAll { it.correlationId in [blank, tooLong]*.header('X-Correlation-Id') }.size() == 2
    }

    def "a reminder of an approval off its list and an edit of a change that is no number leave FAILURE rows"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Reminded ${uniqueCode()}")), [])

        when:
        def reminder = api.post("/api/changes/$raised.id/reminders", [departmentId: raised.departmentId,
                                                                       approval: 'BOSS'])
        def edit = api.put('/api/changes/undefined', [version: 0])

        then:
        reminder.status == 400
        rowOf(reminder) == [USER_NAME: SIGNED_IN, ACTION: 'REMINDER_SENT', OBJECT_TYPE: 'CHANGE',
                            OBJECT_ID: raised.id as Long, OBJECT_KEY: raised.number, OUTCOME: 'FAILURE',
                            MESSAGE: 'approval must be one of BUSINESS, L1, L2, SUPPORT', DURATION_MS: 0L]
        detailsOf(reminder) ==
                """{"id":$raised.id,"request":{"departmentId":$raised.departmentId,"approval":"BOSS"}}""".toString()

        and:
        edit.status == 400
        rowOf(edit).subMap(['ACTION', 'OBJECT_TYPE', 'OBJECT_ID', 'OUTCOME', 'MESSAGE']) ==
                [ACTION: 'CHANGE_EDITED', OBJECT_TYPE: 'CHANGE', OBJECT_ID: null, OUTCOME: 'FAILURE',
                 MESSAGE: 'id must be a whole number']
    }

    def "a callback refused by the validation of its body is recorded as the system that called back"() {
        given:
        def correlation = UUID.randomUUID().toString()

        when:
        def refused = api.post('/api/callbacks/protech', [number: 'CHG7777777', state: 'IMPLEMENTATION'],
                [Authorization: "Bearer $SERVICE_TOKEN".toString(), 'X-Correlation-Id': correlation])

        then:
        refused.status == 400
        rowOf(refused) == [USER_NAME: 'system:protech', ACTION: 'CALLBACK_PROTECH', OBJECT_TYPE: 'CHANGE',
                           OBJECT_ID: null, OBJECT_KEY: 'CHG7777777', OUTCOME: 'FAILURE',
                           MESSAGE: 'event must not be null', DURATION_MS: 0L]
        refused.header('X-Correlation-Id') == correlation
    }

    def "the callbacks are recorded as the system that called back, under the correlation id it passed on"() {
        when:
        callback('/api/callbacks/protech', [number: 'CHG8888888', event: 'TASK_CHANGED', correlationId: CORRELATION])
        callback('/api/callbacks/internal', [system: 'CYBERTRACK', key: 'SCP-8888', status: 'Done',
                                             at: Instant.now().toString(), correlationId: CORRELATION])

        then:
        rowsAbout('CHG8888888')*.subMap(['USER_NAME', 'ACTION', 'OBJECT_TYPE', 'OUTCOME', 'CORRELATION_ID']) == [
                [USER_NAME: 'system:protech', ACTION: 'CALLBACK_PROTECH', OBJECT_TYPE: 'CHANGE', OUTCOME: 'SUCCESS',
                 CORRELATION_ID: CORRELATION]]
        rowsAbout('SCP-8888')*.subMap(['USER_NAME', 'ACTION', 'OBJECT_TYPE', 'OUTCOME', 'CORRELATION_ID']) == [
                [USER_NAME: 'system:internal', ACTION: 'CALLBACK_INTERNAL', OBJECT_TYPE: 'TICKET', OUTCOME: 'SUCCESS',
                 CORRELATION_ID: CORRELATION]]
    }

    def "the action history answers the rows newest first, filtered and paged, with the values of its filters"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "History ${code}"))
        api.put("/api/products/$created.id", product(code: code, name: "History renamed ${code}", version: 0))

        when:
        def page = api.get("/api/action-history?objectKey=${code.toLowerCase()}&size=1")

        then:
        page.status == 200
        page.json.page == 0
        page.json.size == 1
        page.json.total == 2
        page.json.content*.action == ['PRODUCT_UPDATED']
        def row = page.json.content[0] as Map
        row.subMap(['user', 'objectType', 'objectId', 'objectKey', 'outcome']) ==
                [user: SIGNED_IN, objectType: 'PRODUCT', objectId: created.id, objectKey: code, outcome: 'SUCCESS']
        (row.details as String).contains("History renamed ${code}")
        row.correlationId ==~ /[0-9a-f-]{36}/
        row.durationMs >= 0
        api.get("/api/action-history?objectKey=$code&page=1&size=1").json.content*.action == ['PRODUCT_CREATED']
        api.get("/api/action-history?objectKey=$code&action=PRODUCT_CREATED&outcome=FAILURE").json.total == 0
        SIGNED_IN in api.get('/api/action-history/users').json
        api.get('/api/action-history/actions').json.containsAll(['PRODUCT_CREATED', 'PRODUCT_UPDATED'])

        and:
        api.get('/api/action-history?size=201').status == 400
        api.get('/api/action-history?action=CHANGE_VIEWED').status == 400
    }

    def "the first API call of a session records the sign-in, once, and a new session after signing out another"() {
        given:
        def browser = new BeadleClient(api.baseUrl, new CookieManager())
        int before = signIns()

        when:
        def first = browser.get('/api/me')
        browser.get('/api/products')
        browser.get('/api/action-history?size=1')

        then:
        first.status == 200
        first.json.subMap(['name', 'email']) == [name: SIGNED_IN, email: null]
        first.json.admin == true
        signIns() == before + 1
        lastSignIn() == [USER_NAME: SIGNED_IN, OBJECT_TYPE: 'USER', OBJECT_KEY: SIGNED_IN, OUTCOME: 'SUCCESS',
                         CORRELATION_ID: first.header('X-Correlation-Id')]

        when:
        def signedOut = browser.post('/logout')
        browser.get('/api/me')

        then:
        signedOut.status == 204
        signIns() == before + 2
    }

    def "reads leave no row, but for the newer state of a change from ProTech they store"() {
        given:
        api.get('/api/me')
        int before = rowsBut('CHANGE_SYNCED')

        when:
        api.get('/api/products')
        api.get('/api/departments')
        api.get('/api/changes')
        api.get('/api/action-history')

        then:
        rowsBut('CHANGE_SYNCED') == before
    }

    /** The rows of every action but the one given; the demo ProTech moves changes on with its clock at any time. */
    private int rowsBut(String action) {
        jdbc.queryForObject('SELECT COUNT(*) FROM BEADLE_ACTION_HISTORY WHERE ACTION <> ?', Integer, action)
    }

    private int signIns() {
        jdbc.queryForObject("SELECT COUNT(*) FROM BEADLE_ACTION_HISTORY WHERE ACTION = 'USER_SIGNED_IN'", Integer)
    }

    private Map lastSignIn() {
        jdbc.queryForMap('''SELECT USER_NAME, OBJECT_TYPE, OBJECT_KEY, OUTCOME, CORRELATION_ID FROM BEADLE_ACTION_HISTORY
                WHERE ACTION = 'USER_SIGNED_IN' ORDER BY ID DESC FETCH FIRST 1 ROWS ONLY''')
    }

    private List<Map> rowsAbout(String key) {
        jdbc.queryForList('''SELECT USER_NAME, ACTION, OBJECT_TYPE, OBJECT_ID, OUTCOME, MESSAGE, CORRELATION_ID
                FROM BEADLE_ACTION_HISTORY WHERE OBJECT_KEY = ? ORDER BY ID''', key)
                .collect { row -> row.collectEntries { name, value -> [name, value instanceof Number ? value as Long : value] } }
    }

    /** The one row the request left besides a sign-in, found by the correlation id it was answered with. */
    private Map rowOf(BeadleClient.Response response) {
        def rows = jdbc.queryForList('''SELECT USER_NAME, ACTION, OBJECT_TYPE, OBJECT_ID, OBJECT_KEY, OUTCOME, MESSAGE,
                DURATION_MS FROM BEADLE_ACTION_HISTORY WHERE CORRELATION_ID = ? AND ACTION <> 'USER_SIGNED_IN'
                ORDER BY ID''', response.header('X-Correlation-Id')).collect { row ->
            row.collectEntries { name, value -> [name, value instanceof Number ? value as Long : value] }
        }
        assert rows.size() == 1: rows
        rows[0]
    }

    private String detailsOf(BeadleClient.Response response) {
        jdbc.queryForObject('SELECT DETAILS FROM BEADLE_ACTION_HISTORY WHERE CORRELATION_ID = ?', String,
                response.header('X-Correlation-Id'))
    }

    /** The details of a row as JSON, which they must be, and within the 32 KB of the column. */
    private static JsonNode parsed(String details) {
        assert details.getBytes(UTF_8).length <= DETAILS_MAX
        STRICT.readTree(details)
    }

    private List<String> details(String key) {
        jdbc.queryForList('SELECT DETAILS FROM BEADLE_ACTION_HISTORY WHERE OBJECT_KEY = ? ORDER BY ID', String, key)
    }

    private def callback(String path, Map body) {
        def response = api.post(path, body, [Authorization: "Bearer $SERVICE_TOKEN".toString()])
        assert response.status == 202: response
        response
    }
}
