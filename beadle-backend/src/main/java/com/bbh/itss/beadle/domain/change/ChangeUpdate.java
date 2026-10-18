package com.bbh.itss.beadle.domain.change;

import lombok.With;

import java.time.Instant;
import java.util.List;

import static com.bbh.itss.beadle.domain.change.ChangeUpdate.Status.APPLIED;
import static com.bbh.itss.beadle.domain.change.ChangeUpdate.Status.NOT_APPLIED;
import static com.bbh.itss.beadle.domain.change.ChangeUpdate.Status.PENDING;
import static com.bbh.itss.beadle.domain.change.ProductionChange.APPLY_LIMIT;
import static java.util.Objects.requireNonNullElse;
import static lombok.AccessLevel.PACKAGE;

public record ChangeUpdate(Status status, Instant requestedAt, String businessServiceName, List<String> fields,
                           String message, @With(PACKAGE) Instant checkedAt) {

    public static final String NOT_APPLIED_MESSAGE = "A minute later ProTech still held its own values, so Beadle shows those.";

    public enum Status { PENDING, APPLIED, NOT_APPLIED }

    public ChangeUpdate {
        fields = List.copyOf(requireNonNullElse(fields, List.of()));
    }

    public static ChangeUpdate requested(Instant requestedAt, String businessServiceName) {
        return new ChangeUpdate(PENDING, requestedAt, businessServiceName, List.of(), null, null);
    }

    public boolean pending() {
        return status == PENDING;
    }

    ChangeUpdate checked(List<String> unapplied, Instant now) {
        if (!pending()) {
            return this;
        }
        if (unapplied.isEmpty()) {
            return new ChangeUpdate(APPLIED, requestedAt, businessServiceName, List.of(), null, now);
        }
        return now.isBefore(requestedAt.plus(APPLY_LIMIT))
                ? new ChangeUpdate(PENDING, requestedAt, businessServiceName, unapplied, null, now)
                : new ChangeUpdate(NOT_APPLIED, requestedAt, businessServiceName, unapplied, NOT_APPLIED_MESSAGE, now);
    }

    ChangeUpdate waitingFor(List<String> unapplied) {
        return new ChangeUpdate(PENDING, requestedAt, businessServiceName, unapplied, null, checkedAt);
    }
}
