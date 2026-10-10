package com.bbh.itss.dso.portal.adapter.in.web

import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasksJson
import static com.bbh.itss.dso.portal.support.ChangeFixtures.templateJson
import static com.bbh.itss.dso.portal.support.Json.parse
import static com.bbh.itss.dso.portal.support.Json.toJson
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class ChangeProfileRequestValidationSpec extends Specification {

    MockMvc mvc = standaloneSetup(new ProfileController()).setControllerAdvice(new ApiExceptionHandler()).build()

    def "a change profile with #refusal is refused against the path of each field"() {
        when:
        def response = mvc.perform(post('/api/samples/change-profile').contentType(APPLICATION_JSON)
                .content(toJson([version: null, template: templateJson(edits), tasks: tasks]))).andReturn().response
        def problem = parse(response.contentAsString)

        then:
        response.status == 400
        problem.title == title
        problem.errors*.field.sort() == fields

        where:
        refusal                  | edits                                                       | tasks       || title               | fields
        'nested broken values'   | ['privilegedAccess.required': true, 'privilegedAccess.users': (1..3).collect { [user: "U$it", account: it == 3 ? ' ' : "adm_u$it"] }, 'planning.backoutPlan': 'x' * 2001, requestedFor: 'x' * 201, 'timing.installationStart': '6pm'] | tasksJson() || 'Validation failed' | ['template.planning.backoutPlan', 'template.privilegedAccess.users[2].account', 'template.requestedFor', 'template.timing.installationStart']
        'too many users'         | ['privilegedAccess.users': (1..8).collect { [user: "U$it", account: "adm_u$it"] }, 'timing.installationHours': 73, jiraProjectKey: 'ce-rt'] | tasksJson() || 'Validation failed' | ['template.jiraProjectKey', 'template.privilegedAccess.users', 'template.timing.installationHours']
        'missing sections'       | [planning: null, timing: null, downtime: null]              | tasksJson() || 'Validation failed' | ['template.downtime', 'template.planning', 'template.timing']
        'a number that is text'  | ['timing.installationHours': 'many']                        | tasksJson() || 'Malformed request' | ['template.timing.installationHours']
        'no tasks'               | [:]                                                         | []          || 'Validation failed' | ['tasks']
        'missing tasks'          | [:]                                                         | null        || 'Validation failed' | ['tasks']
        'broken tasks'           | [:]                                                         | [[shortDescription: ' ', description: 'x' * 4001, assignedTo: 'x' * 201], null] || 'Validation failed' | ['tasks[0].assignedTo', 'tasks[0].assignmentGroup', 'tasks[0].description', 'tasks[0].shortDescription', 'tasks[1]']
        'too many tasks'         | [:]                                                         | tasksJson(51) || 'Validation failed' | ['tasks']
    }

    def "a complete change profile with the Jira key #key passes the bean validation"() {
        expect:
        mvc.perform(post('/api/samples/change-profile').contentType(APPLICATION_JSON)
                .content(toJson([version: 3, template: templateJson(jiraProjectKey: key), tasks: tasksJson(1)])))
                .andReturn().response.contentAsString == 'CERT'

        where:
        key << ['CERT', 'cert']
    }

    @RestController
    static class ProfileController {

        @PostMapping('/api/samples/change-profile')
        String profile(@jakarta.validation.Valid @RequestBody ChangeProfileRequest request) {
            request.toTemplate().jiraProjectKey()
        }
    }
}
