package com.bbh.itss.beadle.domain.audit;

import lombok.Builder;

import java.time.Instant;

import static com.bbh.itss.beadle.domain.shared.Text.abbreviateBytes;

/**
 * One row of the audit trail: who did what to which object, when, with which request ({@code details}, JSON without
 * secrets), and how it ended. Beadle only ever inserts these rows. The adapters write {@code details} longer than
 * {@link #DETAILS_MAX} as their length and result, so that they stay JSON; cutting the text here is only the last
 * guard of the column.
 */
@Builder(toBuilder = true)
public record ActionRecord(Long id, Instant at, String user, Action action, ObjectType objectType, Long objectId,
                           String objectKey, String details, ActionOutcome outcome, String message,
                           String correlationId, long durationMs) {

    public static final int USER_MAX = 200;
    public static final int OBJECT_KEY_MAX = 200;
    public static final int DETAILS_MAX = 256 * 1024;
    public static final int MESSAGE_MAX = 4000;
    public static final int CORRELATION_ID_MAX = 64;
    /** The property of {@code details}, last, with what the use case noted besides its arguments. */
    public static final String RESULT = "result";
    /** The property of {@link #RESULT}, last, with the keys of what the action created in another system. */
    public static final String CREATED = "created";

    public ActionRecord {
        user = abbreviateBytes(user, USER_MAX);
        objectKey = abbreviateBytes(objectKey, OBJECT_KEY_MAX);
        details = abbreviateBytes(details, DETAILS_MAX);
        message = abbreviateBytes(message, MESSAGE_MAX);
        correlationId = abbreviateBytes(correlationId, CORRELATION_ID_MAX);
    }
}
