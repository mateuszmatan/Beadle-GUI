package com.bbh.itss.beadle.frontend.support

import groovy.transform.Immutable

import static groovy.json.JsonOutput.toJson

@Immutable
class StubResponse {

    int status
    String contentType
    String body
    /** Further response headers, such as the {@code Location} of a redirect. */
    Map<String, String> headers

    static StubResponse json(Object value, int status = 200) {
        new StubResponse(status: status, contentType: 'application/json', body: toJson(value))
    }

    static StubResponse problem(int status, String title, String detail, Map<String, Object> extra = [:]) {
        new StubResponse(status: status, contentType: 'application/problem+json',
                body: toJson([status: status, title: title, detail: detail] + extra))
    }

    static StubResponse empty(int status = 204) {
        new StubResponse(status: status, contentType: 'application/json', body: '')
    }

    static StubResponse redirect(String location) {
        new StubResponse(status: 302, contentType: 'text/plain', body: '', headers: [Location: location])
    }
}
