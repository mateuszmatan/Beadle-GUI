package com.bbh.itss.beadle.adapter.in.web

import com.bbh.itss.beadle.application.change.ChangeProfileService
import com.bbh.itss.beadle.application.change.port.in.ChangeOptionsUseCase
import com.bbh.itss.beadle.application.change.port.in.LookupsUseCase
import com.bbh.itss.beadle.application.change.port.in.ProductionChangesUseCase
import com.bbh.itss.beadle.application.change.port.out.ChangeProductsPort
import com.bbh.itss.beadle.application.change.port.out.ChangeProfileRepositoryPort
import com.bbh.itss.beadle.domain.change.ChangeProfile
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static com.bbh.itss.beadle.support.ChangeFixtures.changeProduct
import static com.bbh.itss.beadle.support.ChangeFixtures.tasksJson
import static com.bbh.itss.beadle.support.ChangeFixtures.templateJson
import static com.bbh.itss.beadle.support.Json.parse
import static com.bbh.itss.beadle.support.Json.toJson
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

/** PUT /api/products/{id}/change-profile reads the body into the domain records, whose own checks refuse it. */
class ChangeProfileRequestValidationSpec extends Specification {

    ChangeProfileRepositoryPort profiles = Stub() {
        find(_) >> Optional.empty()
        save(_) >> { ChangeProfile profile -> profile }
    }
    ChangeProductsPort products = Stub() {
        get(_) >> changeProduct()
    }
    MockMvc mvc = standaloneSetup(new ChangeController(Mock(ProductionChangesUseCase),
            new ChangeProfileService(profiles, products), Mock(ChangeOptionsUseCase), Mock(LookupsUseCase)))
            .setControllerAdvice(new ApiExceptionHandler(Mock(RefusedRequests)), new NormalizedRequestBodies())
            .build()

    def "a change profile with #refusal is refused against the path of each field"() {
        when:
        def response = save([version: null, template: templateJson(edits), tasks: tasks])
        def problem = parse(response.contentAsString)

        then:
        response.status == 400
        problem.title == title
        problem.problems*.field.sort() == fields

        where:
        refusal                  | edits                                                       | tasks       || title               | fields
        'nested broken values'   | ['privilegedAccess.required': true, 'privilegedAccess.users': (1..3).collect { [user: "U$it", account: it == 3 ? ' ' : "adm_u$it"] }, 'planning.backoutPlan': 'x' * 2001, requestedFor: 'x' * 201, 'timing.installationStart': '6pm'] | tasksJson() || 'Validation failed' | ['template.planning.backoutPlan', 'template.privilegedAccess.users[2].account', 'template.requestedFor', 'template.timing.installationStart']
        'too many users'         | ['privilegedAccess.required': true, 'privilegedAccess.users': (1..8).collect { [user: "U$it", account: "adm_u$it"] }, 'timing.installationHours': 73, jiraProjectKey: 'ce-rt'] | tasksJson() || 'Validation failed' | ['template.jiraProjectKey', 'template.privilegedAccess.users', 'template.timing.installationHours']
        'missing sections'       | [planning: null, timing: null, downtime: null]              | tasksJson() || 'Validation failed' | ['template.planning', 'template.timing']
        'a number that is text'  | ['timing.installationHours': 'many']                        | tasksJson() || 'Malformed request' | ['template.timing.installationHours']
        'no tasks'               | [:]                                                         | []          || 'Validation failed' | ['tasks']
        'missing tasks'          | [:]                                                         | null        || 'Validation failed' | ['tasks']
        'broken tasks'           | [:]                                                         | [[shortDescription: ' ', description: 'x' * 4001, assignedTo: 'x' * 201]] || 'Validation failed' | ['tasks[0].assignedTo', 'tasks[0].assignmentGroup', 'tasks[0].description', 'tasks[0].shortDescription']
        'a task that is null'    | [:]                                                         | tasksJson(1) + [null] || 'Validation failed' | ['tasks[1]']
        'too many tasks'         | [:]                                                         | tasksJson(51) || 'Validation failed' | ['tasks']
    }

    def "a complete change profile with the Jira key #key is saved with the key in upper case"() {
        when:
        def response = save([version: 3, template: templateJson(jiraProjectKey: key), tasks: tasksJson(1)])

        then:
        response.status == 200
        parse(response.contentAsString).template.jiraProjectKey == 'CERT'

        where:
        key << ['CERT', ' cert ']
    }

    private def save(Map body) {
        mvc.perform(put('/api/products/1/change-profile').contentType(APPLICATION_JSON).content(toJson(body)))
                .andReturn().response
    }
}
