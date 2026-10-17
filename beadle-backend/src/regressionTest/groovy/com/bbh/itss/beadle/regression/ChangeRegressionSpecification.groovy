package com.bbh.itss.beadle.regression

import com.bbh.itss.beadle.support.BeadleClient
import com.bbh.itss.beadle.support.BeadleSpecification

import java.time.Instant

import static com.bbh.itss.beadle.support.ChangeFixtures.changeTasksJson
import static com.bbh.itss.beadle.support.ChangeFixtures.scheduleJson
import static com.bbh.itss.beadle.support.ChangeFixtures.templateJson
import static java.net.URLEncoder.encode
import static java.nio.charset.StandardCharsets.UTF_8
import static java.time.temporal.ChronoUnit.DAYS
import static java.time.temporal.ChronoUnit.HOURS

abstract class ChangeRegressionSpecification extends BeadleSpecification {

    static final Instant START = Instant.now().plus(3, DAYS).truncatedTo(HOURS)
    static final String SIGNED_IN = 'Mateusz Matan'

    /** Raises a change of {@code product} from the first epic of its newest FixVersion and creates its tasks. */
    protected Map raise(Map product, List tasks = changeTasksJson()) {
        String key = 'CHG' + product.id
        String fixVersion = api.get("/api/products/$product.id/jira/versions?project=$key").json[0].name
        String epic = api.get("/api/products/$product.id/jira/epics?fixVersion=${enc(fixVersion)}&project=$key")
                .json[0].key
        def response = api.post('/api/changes', change(product, fixVersion, [epic], [],
                [template: templateJson(jiraProjectKey: key)]))
        assert response.status == 201: response
        tasks ? createTasks(response.json as Map, tasks) : response.json as Map
    }

    protected Map createTasks(Map change, List tasks) {
        def response = api.post("/api/changes/$change.id/tasks", [version: change.version,
                                                                   departmentId: change.departmentId, tasks: tasks])
        assert response.status == 200: response
        response.json as Map
    }

    protected static Map editOf(Map change, Map edits) {
        [version: change.version, departmentId: change.departmentId, shortDescription: change.shortDescription,
         description: change.description, schedule: change.schedule, template: change.template,
         tasks: change.tasks] + edits
    }

    protected static Map change(Map product, String fixVersion, List epicKeys, List storyKeys = [], Map edits = [:]) {
        [productId: product.id, fixVersion: fixVersion, epicKeys: epicKeys, storyKeys: storyKeys,
         schedule: scheduleJson(START), template: templateJson()] + edits
    }

    protected BeadleClient.Response callback(String path, Map body, Map<String, String> headers = [:]) {
        api.post(path, body, [Authorization: "Bearer $SERVICE_TOKEN".toString()] + headers)
    }

    protected static String enc(String text) {
        encode(text, UTF_8).replace('+', '%20')
    }
}
