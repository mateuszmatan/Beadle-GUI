package com.bbh.itss.beadle.adapter

import com.bbh.itss.beadle.domain.audit.ActionRecord
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

class AuditDetailsSpec extends Specification {

    static final String RESULT = '{"reminded":[{"role":"SUPPORT","people":["Jane Smith"]}],"created":["CTASK0041003"]}'

    JsonMapper json = JsonMapper.builder().build()

    def "details that fit are written as they are"() {
        given:
        def details = tree('{"id":7,"command":{"name":"' + 'é' * (ActionRecord.DETAILS_MAX / 2 - 40) + '"}}')

        expect:
        AuditDetails.write(json, details) == json.writeValueAsString(details)
        AuditDetails.write(json, null) == null
    }

    def "details too long give way to their length#and"() {
        given:
        def details = tree('{"id":7,"command":{"name":"' + 'é' * (ActionRecord.DETAILS_MAX / 2) + '"}}')
        if (result) {
            details.set(ActionRecord.RESULT, tree(result))
        }
        def whole = json.writeValueAsBytes(details).length

        expect:
        AuditDetails.write(json, details) == '{"truncated":true,"bytes":' + whole + written + '}'

        where:
        result || written
        null   || ''
        RESULT || ',"result":' + RESULT

        and = result ? ' followed by the whole result' : ''
    }

    private ObjectNode tree(String text) {
        json.readTree(text) as ObjectNode
    }
}
