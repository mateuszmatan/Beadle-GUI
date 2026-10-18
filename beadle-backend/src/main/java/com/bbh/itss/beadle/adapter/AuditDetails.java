package com.bbh.itss.beadle.adapter;

import com.bbh.itss.beadle.domain.audit.ActionRecord;
import lombok.NoArgsConstructor;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static java.nio.charset.StandardCharsets.UTF_8;
import static lombok.AccessLevel.PRIVATE;

/**
 * Writes the {@code DETAILS} of an audit row as JSON. Details longer than {@link ActionRecord#DETAILS_MAX} bytes, far
 * more than any request Beadle accepts, give way to {@code {"truncated": true, "bytes": 123456}} (the length of the
 * whole JSON) followed by the {@value ActionRecord#RESULT} property, so that they stay JSON and keep the keys of what
 * the action created. The audit interceptor writes the arguments of a use case with it, and the API the body of a
 * request it refused.
 */
@NoArgsConstructor(access = PRIVATE)
public final class AuditDetails {

    /** The property that says the details were too long to keep, first in their place. */
    public static final String TRUNCATED = "truncated";
    /** The property, after {@value #TRUNCATED}, with the length in bytes of the details as JSON. */
    public static final String BYTES = "bytes";

    /** The details as JSON of at most {@link ActionRecord#DETAILS_MAX} bytes but for a long result; null for none. */
    public static String write(JsonMapper json, ObjectNode details) {
        if (details == null) {
            return null;
        }
        byte[] whole = json.writeValueAsBytes(details);
        if (whole.length <= ActionRecord.DETAILS_MAX) {
            return new String(whole, UTF_8);
        }
        ObjectNode truncated = json.createObjectNode().put(TRUNCATED, true).put(BYTES, whole.length);
        if (details.has(ActionRecord.RESULT)) {
            truncated.set(ActionRecord.RESULT, details.get(ActionRecord.RESULT));
        }
        return json.writeValueAsString(truncated);
    }
}
