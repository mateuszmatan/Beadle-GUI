package com.bbh.itss.beadle.adapter.in.web

import com.bbh.itss.beadle.adapter.AuditDetails
import com.bbh.itss.beadle.application.audit.port.in.ActionHistoryUseCase
import com.bbh.itss.beadle.domain.audit.Action
import com.bbh.itss.beadle.domain.audit.ActionObject
import com.bbh.itss.beadle.domain.audit.ActionRecord
import com.bbh.itss.beadle.domain.audit.ActionRecord.ActionRecordBuilder
import com.bbh.itss.beadle.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.domain.shared.InvalidRequestException.FieldProblem
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import spock.lang.Specification
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset

import static com.bbh.itss.beadle.domain.audit.Action.BUSINESS_SERVICE_CREATED
import static com.bbh.itss.beadle.domain.audit.Action.BUSINESS_SERVICE_RENAMED
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_RAISED
import static com.bbh.itss.beadle.domain.audit.ActionOutcome.FAILURE
import static com.bbh.itss.beadle.domain.audit.ObjectType.BUSINESS_SERVICE
import static com.bbh.itss.beadle.domain.audit.ObjectType.PRODUCT
import static com.bbh.itss.beadle.support.Json.parse
import static com.bbh.itss.beadle.support.Json.toJson
import static java.nio.charset.StandardCharsets.UTF_8
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.http.MediaType.TEXT_PLAIN
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class RefusedRequestsSpec extends Specification {

    static final String DAYS = 'must be one of MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY'

    static final Instant NOW = Instant.parse('2026-10-15T09:00:00Z')

    ActionHistoryUseCase history = Mock()
    List<Refused> recorded = []

    MockMvc mvc = mvc(history)

    def setup() {
        history.record(_, _) >> { ActionRecordBuilder row, ActionObject object ->
            def built = row.build()
            assert built.at() == NOW && built.outcome() == FAILURE && built.durationMs() == 0
            recorded << new Refused(built.action(), object, built.details(), built.message())
            built
        }
    }

    def "a #refusal is recorded as a FAILURE of the endpoint's action with the body as sent and why it was refused"() {
        when:
        def response = mvc.perform(json(post('/api/samples/named'), body)).andReturn().response

        then:
        response.status == 400
        recorded == [new Refused(BUSINESS_SERVICE_CREATED, ActionObject.keyed(BUSINESS_SERVICE, key), details, message)]
        parse(response.contentAsString).detail != null

        where:
        refusal                  | body                                   || key          | details                                                    | message
        'blank field'            | '{"name": " "}'                        || null         | '{"request":{"name":" "}}'                                 | 'name must not be blank'
        'field too long'         | '{"name": "Fund Services Europe"}'     || 'Fund Services Europe' | '{"request":{"name":"Fund Services Europe"}}'     | 'name size must be between 0 and 10'
        'enum value off its list'| '{"name": "Ops", "day": "FUNDAY"}'     || 'Ops'        | '{"request":{"name":"Ops","day":"FUNDAY"}}'                | "day $DAYS"
        'body that is no JSON'   | '{"name": '                            || null         | '{"request":null}'                                         | 'The request body is not valid JSON.'
        'missing body'           | ''                                     || null         | '{"request":null}'                                         | 'The request body is missing.'
    }

    def "the secrets of a refused body are masked"() {
        when:
        mvc.perform(json(post('/api/samples/named'), '{"name": "", "apiToken": "s3cret", "nested": {"password": "p"}}'))

        then:
        recorded*.details() == ['{"request":{"name":"","apiToken":"*****","nested":{"password":"*****"}}}']
    }

    def "the {id} of the path is the object, and a path value of the wrong type is refused with its field"() {
        when:
        mvc.perform(json(put('/api/samples/named/7'), '{"name": ""}'))
        mvc.perform(json(put('/api/samples/named/seven'), '{"name": "Ops"}'))

        then:
        recorded == [
                new Refused(BUSINESS_SERVICE_RENAMED, ActionObject.of(BUSINESS_SERVICE, 7), '{"id":7,"request":{"name":""}}',
                        'name must not be blank'),
                new Refused(BUSINESS_SERVICE_RENAMED, ActionObject.of(BUSINESS_SERVICE, null),
                        '{"id":"seven","request":null}', 'id must be a whole number')]
    }

    def "a refused raise is filed under the product its body names"() {
        when:
        mvc.perform(json(post('/api/samples/raised'), '{"productId": 6, "fixVersion": ""}'))
        mvc.perform(json(post('/api/samples/raised'), '{"productId": "six"}'))

        then:
        recorded*.action() == [CHANGE_RAISED, CHANGE_RAISED]
        recorded*.object() == [ActionObject.of(PRODUCT, 6), ActionObject.of(PRODUCT, null)]
        recorded*.message() == ['fixVersion must not be blank', 'productId must be a whole number']
    }

    def "a refused body of 100 KB is recorded as JSON within 32 KB, its long string cut and the rest as sent"() {
        given:
        def body = toJson([productId: 6, fixVersion: '', description: 'T' * 100_000, epicKeys: ['CERT-1', 'CERT-2']])

        when:
        def response = mvc.perform(json(post('/api/samples/raised'), body)).andReturn().response

        then:
        response.status == 400
        recorded*.message() == ['fixVersion must not be blank']
        def details = recorded[0].details()
        details.getBytes(UTF_8).length <= ActionRecord.DETAILS_MAX
        details.getBytes(UTF_8).length > ActionRecord.DETAILS_MAX - 4
        def request = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build()
                .readTree(details).get('request')
        request.get('productId').asLong() == 6L
        request.get('fixVersion').stringValue() == ''
        request.get('description').stringValue() ==~ /T{30000,}/ + AuditDetails.CUT
        request.get('epicKeys')*.stringValue() == ['CERT-1', 'CERT-2']
    }

    def "a body of another content type is refused and recorded as well"() {
        when:
        def response = mvc.perform(post('/api/samples/named').contentType(TEXT_PLAIN).content('Ops')).andReturn().response

        then:
        response.status == 415
        recorded*.message() == ["Content-Type 'text/plain' is not supported."]
    }

    def "nothing is recorded for #what"() {
        when:
        def response = mvc.perform(json(post(path), body)).andReturn().response

        then:
        response.status == status
        recorded.empty

        where:
        what                                               | path                      | body              || status
        'an endpoint that changes nothing'                 | '/api/samples/unaudited'  | '{"name": ""}'    || 400
        'a request the use case refused, which records it' | '/api/samples/refusing'   | '{"name": "Ops"}' || 400
        'a request that was not refused'                   | '/api/samples/named'      | '{"name": "Ops"}' || 200
    }

    def "a row that cannot be written leaves the answer as it is"() {
        given:
        def failing = Mock(ActionHistoryUseCase) {
            record(_, _) >> { throw new IllegalStateException('the database is down') }
        }
        def mvc = mvc(failing)

        when:
        def response = mvc.perform(json(post('/api/samples/named'), '{"name": ""}')).andReturn().response

        then:
        response.status == 400
        parse(response.contentAsString).problems == [[field: 'name', message: 'must not be blank']]
    }

    def "every endpoint that changes something names its action, so that a refused request leaves a row"() {
        given:
        def scanner = new ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController))
        def controllers = scanner.findCandidateComponents('com.bbh.itss.beadle.adapter.in.web')
                .collect { Class.forName(it.beanClassName) }
                .findAll { it.protectionDomain.codeSource.location == ChangeController.protectionDomain.codeSource.location }

        when:
        def unaudited = controllers.collectMany { Class type ->
            type.declaredMethods.findAll { method ->
                [PostMapping, PutMapping, DeleteMapping, PatchMapping].any { method.isAnnotationPresent(it) } &&
                        !method.isAnnotationPresent(AuditedRequest)
            }.collect { "${type.simpleName}.${it.name}" }
        }

        then:
        controllers*.simpleName.containsAll(['ChangeController', 'ProductController', 'BusinessServiceController',
                                             'CallbackController'])
        unaudited == []
    }

    private static MockMvc mvc(ActionHistoryUseCase history) {
        def refusals = new RefusedRequests(history, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC))
        standaloneSetup(new AuditedSampleController())
                .setControllerAdvice(new ApiExceptionHandler(refusals), new KeptRequestBodies(),
                        new NormalizedRequestBodies())
                .build()
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        request.contentType(APPLICATION_JSON).content(body)
    }

    /** What a refused request recorded. */
    record Refused(Action action, ActionObject object, String details, String message) {
    }

    /** Endpoints of every kind of {@link AuditedRequest}. */
    @RestController
    static class AuditedSampleController {

        @PostMapping('/api/samples/named')
        @AuditedRequest(value = BUSINESS_SERVICE_CREATED, objectKey = 'name')
        String create(@Valid @RequestBody Named request) {
            request.name
        }

        @PutMapping('/api/samples/named/{id}')
        @AuditedRequest(BUSINESS_SERVICE_RENAMED)
        String rename(@PathVariable('id') long id, @Valid @RequestBody Named request) {
            "$id $request.name"
        }

        @PostMapping('/api/samples/raised')
        @AuditedRequest(value = CHANGE_RAISED, objectId = 'productId', objectType = PRODUCT)
        String raise(@Valid @RequestBody Raised request) {
            request.fixVersion
        }

        @PostMapping('/api/samples/refusing')
        @AuditedRequest(BUSINESS_SERVICE_CREATED)
        String refusing(@Valid @RequestBody Named request) {
            throw new InvalidRequestException([new FieldProblem('name', 'is taken')])
        }

        @PostMapping('/api/samples/unaudited')
        String unaudited(@Valid @RequestBody Named request) {
            request.name
        }
    }

    static class Named {

        @NotBlank
        @Size(max = 10)
        String name
        String apiToken
        DayOfWeek day
    }

    static class Raised {

        @NotNull
        Long productId

        @NotBlank
        String fixVersion
    }
}
