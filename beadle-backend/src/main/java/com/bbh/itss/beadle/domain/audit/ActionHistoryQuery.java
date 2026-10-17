package com.bbh.itss.beadle.domain.audit;

import com.bbh.itss.beadle.domain.shared.ValidationProblems;

import java.time.Instant;

import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * The rows of the audit trail asked for: every filter is optional, {@code from} is inclusive and {@code to}
 * exclusive, and the rows come newest first in pages of at most {@value #MAX_SIZE}.
 */
public record ActionHistoryQuery(String user, Action action, ObjectType objectType, String objectKey,
                                 ActionOutcome outcome, Instant from, Instant to, int page, int size) {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;
    public static final String PAGE_MESSAGE = "must be 0 or more";
    public static final String SIZE_MESSAGE = "must be between 1 and " + MAX_SIZE;
    public static final String RANGE_MESSAGE = "must be before to";

    public ActionHistoryQuery {
        user = trimToNull(user);
        objectKey = trimToNull(objectKey);
    }

    public static ActionHistoryQuery of(String user, Action action, ObjectType objectType, String objectKey,
                                        ActionOutcome outcome, Instant from, Instant to, Integer page,
                                        Integer size) {
        ActionHistoryQuery query = new ActionHistoryQuery(user, action, objectType, objectKey, outcome, from, to,
                getIfNull(page, 0), getIfNull(size, DEFAULT_SIZE));
        ValidationProblems problems = new ValidationProblems();
        if (query.page() < 0) {
            problems.add("page", PAGE_MESSAGE);
        }
        if (query.size() < 1 || query.size() > MAX_SIZE) {
            problems.add("size", SIZE_MESSAGE);
        }
        if (from != null && to != null && !from.isBefore(to)) {
            problems.add("from", RANGE_MESSAGE);
        }
        problems.throwIfAny();
        return query;
    }
}
