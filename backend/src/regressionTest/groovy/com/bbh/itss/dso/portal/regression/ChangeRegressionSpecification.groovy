package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.PortalSpecification

import java.time.Instant

import static com.bbh.itss.dso.portal.support.ChangeFixtures.scheduleJson
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasksJson
import static com.bbh.itss.dso.portal.support.ChangeFixtures.templateJson
import static java.net.URLEncoder.encode
import static java.nio.charset.StandardCharsets.UTF_8
import static java.time.temporal.ChronoUnit.DAYS
import static java.time.temporal.ChronoUnit.HOURS

abstract class ChangeRegressionSpecification extends PortalSpecification {

    static final Instant START = Instant.now().plus(3, DAYS).truncatedTo(HOURS)

    protected Map raise(Map product) {
        String key = 'CHG' + product.id
        String fixVersion = api.get("/api/products/$product.id/jira/versions?project=$key").json[0].name
        String epic = api.get("/api/products/$product.id/jira/epics?fixVersion=${enc(fixVersion)}&project=$key")
                .json[0].key
        def response = api.post('/api/changes', change(product, fixVersion, [epic], [],
                [template: templateJson(jiraProjectKey: key)]))
        assert response.status == 201: response
        response.json as Map
    }

    protected static Map editOf(Map change, Map edits) {
        [version: change.version, departmentId: change.departmentId, shortDescription: change.shortDescription,
         description: change.description, schedule: change.schedule, template: change.template,
         tasks: change.tasks] + edits
    }

    protected static Map change(Map product, String fixVersion, List epicKeys, List storyKeys = [], Map edits = [:]) {
        [productId: product.id, fixVersion: fixVersion, epicKeys: epicKeys, storyKeys: storyKeys,
         schedule: scheduleJson(START), template: templateJson(), tasks: tasksJson()] + edits
    }

    protected static String enc(String text) {
        encode(text, UTF_8).replace('+', '%20')
    }
}
