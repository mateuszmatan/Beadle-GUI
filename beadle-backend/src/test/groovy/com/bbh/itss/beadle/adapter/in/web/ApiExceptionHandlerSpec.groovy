package com.bbh.itss.beadle.adapter.in.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.bbh.itss.beadle.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.domain.shared.InvalidRequestException.FieldProblem
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import spock.lang.Specification

import java.time.DayOfWeek
import java.time.Instant

import static com.bbh.itss.beadle.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.beadle.domain.shared.Failures.notFound
import static com.bbh.itss.beadle.support.Json.parse
import static com.bbh.itss.beadle.support.Json.toJson
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.http.MediaType.TEXT_PLAIN
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class ApiExceptionHandlerSpec extends Specification {

    static final FieldProblem PROBLEM = new FieldProblem('services[0].build.javaPath', 'is required')
    static final String UNEXPECTED = "Beadle could not handle the request. The failure is in Beadle's log."
    static final String DAYS = 'must be one of MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY'

    def handler = new ApiExceptionHandler()

    MockMvc mvc = standaloneSetup(new SampleController()).setControllerAdvice(handler).build()

    def "#failure is #status #title"() {
        expect:
        with(answer(handler)) {
            it.status == status
            it.title == title
            it.detail == detail
            it.properties?.problems == problems
            !it.properties?.containsKey('errors')
        }

        where:
        failure                    | answer                                                 || status | title
        'a missing record'         | { it.notFound(notFound('Product', 7)) }                || 404    | 'Not found'
        'a clash with stored data' | { it.conflict(new IllegalStateException('code clash')) }  || 409    | 'Conflict'
        'a concurrent change'      | { it.staleData(new ObjectOptimisticLockingFailureException(Object, 1L)) } || 409 | 'Conflict'
        'a broken business rule'   | { it.invalid(new InvalidRequestException([PROBLEM])) } || 400   | 'Validation failed'
        'an unexpected failure'    | { it.unexpected(new IllegalStateException('at com.bbh')) } || 500 | 'Request failed'
        'an unreachable system'    | { it.unavailable(new UncheckedIOException('ProTech timed out', new IOException())) } || 503 | 'Service Unavailable'

        detail << ['Product 7 does not exist', 'code clash', STALE_VERSION, 'is required', UNEXPECTED,
                   'ProTech timed out']
        problems << [null, null, null, [PROBLEM], null, null]
    }

    def "names Beadle, not the portal it came from, in what it logs of a failure"() {
        given:
        def logged = new ListAppender<ILoggingEvent>()
        def logger = LoggerFactory.getLogger(ApiExceptionHandler) as Logger
        logged.start()
        logger.addAppender(logged)

        when:
        handler.unavailable(new UncheckedIOException('Beadle-ProTech did not answer', new IOException()))
        handler.unexpected(new IllegalStateException('at com.bbh'))

        then:
        logged.list*.formattedMessage == ['A system Beadle relies on could not be reached: Beadle-ProTech did not answer',
                                          'Beadle could not serve a request']

        cleanup:
        logger.detachAppender(logged)
    }

    def "a violated database constraint is 409 without the constraint's name"() {
        when:
        def problem = handler.integrity(new DataIntegrityViolationException('UK_BEADLE_PRODUCT_CODE'))

        then:
        problem.status == 409
        problem.detail.contains('duplicate')
        !problem.detail.contains('UK_BEADLE_PRODUCT_CODE')
    }

    def "bean validation errors are listed per field under problems, as the contract of the services names them"() {
        when:
        def response = mvc.perform(json(toJson([day: 'FRIDAY', labels: labels]))).andReturn().response

        then:
        response.status == 400
        with(parse(response.contentAsString)) {
            title == 'Validation failed'
            it.detail == detail
            problems*.field == fields
            it.errors == null
        }

        where:
        labels       || detail                   | fields
        []           || 'add at least one label' | ['labels']
        ['a', 'b,c'] || 'must not contain commas' | ['labels[1]']
    }

    def "#request answers #status #title without naming a class or a method"() {
        when:
        def response = mvc.perform(builder).andReturn().response

        then:
        response.status == status
        response.contentType.startsWith('application/problem+json')
        with(parse(response.contentAsString)) {
            it.title == title
            it.detail == detail
            it.problems == problems
        }

        where:
        request                  | builder                                           || status | title                    | detail | problems
        'a failing controller'   | get('/api/samples/7')                             || 500    | 'Request failed'         | UNEXPECTED | null
        'no body'                | json('')                                          || 400    | 'Malformed request'      | 'The request body is missing.' | null
        'a body that is no JSON' | json('{"type":')                                  || 400    | 'Malformed request'      | 'The request body is not valid JSON.' | null
        'a body of another shape'| json('["FRIDAY"]')                                  || 400    | 'Malformed request'      | 'The request body does not have the shape this endpoint expects.' | null
        'a path value'           | get('/api/samples/undefined')                     || 400    | 'Malformed request'      | "The value given for 'id' is not one this endpoint can read." | [[field: 'id', message: 'must be a whole number']]
        'a query value'          | get('/api/samples').param('size', 'big')          || 400    | 'Malformed request'      | "The value given for 'size' is not one this endpoint can read." | [[field: 'size', message: 'must be a whole number']]
        'a query enum'           | get('/api/samples/days').param('day', 'FUNDAY')   || 400    | 'Malformed request'      | "The value given for 'day' is not one this endpoint can read." | [[field: 'day', message: DAYS]]
        'a query instant'        | get('/api/samples/days').param('from', 'yesterday') || 400  | 'Malformed request'      | "The value given for 'from' is not one this endpoint can read." | [[field: 'from', message: 'must be an instant in UTC, such as 2026-10-15T09:00:00Z']]
        'no query value'         | get('/api/samples')                               || 400    | 'Bad Request'            | "Required parameter 'size' is not present." | null
        'another method'         | post('/api/samples/7')                            || 405    | 'Method Not Allowed'     | "Method 'POST' is not supported." | null
        'another content type'   | post('/api/samples').contentType(TEXT_PLAIN).content('FRIDAY') || 415 | 'Unsupported Media Type' | "Content-Type 'text/plain' is not supported." | null
    }

    def "a value of the body that cannot be read is listed under problems with its field"() {
        when:
        def response = mvc.perform(json('{"day": "FUNDAY", "labels": ["a"]}')).andReturn().response

        then:
        response.status == 400
        with(parse(response.contentAsString)) {
            title == 'Malformed request'
            it.detail == "day $ApiExceptionHandlerSpec.DAYS".toString()
            problems == [[field: 'day', message: ApiExceptionHandlerSpec.DAYS]]
            it.errors == null
        }

    }

    private static MockHttpServletRequestBuilder json(String body) {
        post('/api/samples').contentType(APPLICATION_JSON).content(body)
    }

    @RestController
    static class SampleController {

        @GetMapping('/api/samples/{id}')
        String read(@PathVariable('id') Long id) {
            throw new UnsupportedOperationException("no sample $id is loaded")
        }

        @GetMapping('/api/samples')
        String list(@RequestParam('size') int size) {
            "$size samples"
        }

        @GetMapping('/api/samples/days')
        String days(@RequestParam(name = 'day', required = false) DayOfWeek day,
                    @RequestParam(name = 'from', required = false) Instant from) {
            "$day from $from"
        }

        @PostMapping('/api/samples')
        String create(@jakarta.validation.Valid @RequestBody SampleRequest request) {
            request.day().name()
        }
    }
}
