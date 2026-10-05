package com.bbh.itss.dso.portal.gui.support

import groovy.json.JsonSlurper
import groovy.transform.Immutable

@Immutable
class RecordedRequest {

    String method
    String path
    String query
    String body

    Object json() {
        body ? new JsonSlurper().parseText(body) : null
    }

    Map<String, String> params() {
        query ? query.split('&').collectEntries { pair ->
            def parts = pair.split('=', 2)
            [(URLDecoder.decode(parts[0], 'UTF-8')): parts.length > 1 ? URLDecoder.decode(parts[1], 'UTF-8') : '']
        } : [:]
    }
}
