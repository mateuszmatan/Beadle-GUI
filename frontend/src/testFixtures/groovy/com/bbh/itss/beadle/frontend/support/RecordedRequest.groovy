package com.bbh.itss.beadle.frontend.support

import groovy.json.JsonSlurperClassic
import groovy.transform.Immutable

@Immutable
class RecordedRequest {

    String method
    String path
    String query
    String body
    /** The request headers by lower-case name, the first value of each. */
    Map<String, String> headers

    String header(String name) {
        headers?.get(name.toLowerCase(Locale.ROOT))
    }

    /** The body as plain maps and lists, which a handler may keep in state that other server threads read. */
    Object json() {
        body ? new JsonSlurperClassic().parseText(body) : null
    }

    Map<String, String> params() {
        query ? query.split('&').collectEntries { pair ->
            def parts = pair.split('=', 2)
            [(URLDecoder.decode(parts[0], 'UTF-8')): parts.length > 1 ? URLDecoder.decode(parts[1], 'UTF-8') : '']
        } : [:]
    }
}
