package com.bbh.itss.dso.portal.gui.support

import groovy.json.JsonOutput
import groovy.transform.Immutable

@Immutable
class StubResponse {

    int status
    String contentType
    String body

    static StubResponse json(Object value, int status = 200) {
        new StubResponse(status: status, contentType: 'application/json', body: JsonOutput.toJson(value))
    }

    static StubResponse problem(int status, String title, String detail, Map<String, Object> extra = [:]) {
        new StubResponse(status: status, contentType: 'application/problem+json',
                body: JsonOutput.toJson([status: status, title: title, detail: detail] + extra))
    }

    static StubResponse yaml(String text) {
        new StubResponse(status: 200, contentType: 'application/yaml', body: text)
    }

    static StubResponse empty(int status = 204) {
        new StubResponse(status: status, contentType: 'application/json', body: '')
    }
}
