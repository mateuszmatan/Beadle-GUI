package com.bbh.itss.beadle.application.audit;

import com.bbh.itss.beadle.application.CallContext;
import com.bbh.itss.beadle.application.UseCase;
import com.bbh.itss.beadle.application.audit.port.in.RefusedRequest;
import com.bbh.itss.beadle.application.audit.port.in.RefusedRequestsUseCase;
import com.bbh.itss.beadle.application.audit.port.out.ActionHistoryPort;
import com.bbh.itss.beadle.application.audit.port.out.ObjectKeyPort;
import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort;
import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.ActionRecord;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

import static com.bbh.itss.beadle.domain.audit.ActionOutcome.FAILURE;
import static com.bbh.itss.beadle.domain.shared.Timestamps.now;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

/**
 * Writes the {@code FAILURE} row of a request the API refused before the use case of its action ran, the row the audit
 * interceptor writes for a use case that throws: who (the system a callback acts for, else the signed-in user), the
 * action, its object with the key read from the database when only the id is known, the request, the reason and the
 * correlation id of the request. It takes no time of a use case, so its duration is 0.
 */
@UseCase
@RequiredArgsConstructor
public class RefusedRequestService implements RefusedRequestsUseCase {

    private final ActionHistoryPort history;
    private final ObjectKeyPort keys;
    private final SignedInUserPort users;
    private final Clock clock;

    @Override
    public void refused(RefusedRequest request) {
        ActionObject object = keyed(request.object() == null
                ? ActionObject.of(request.action().objectType(), null) : request.object());
        history.record(ActionRecord.about(request.action(), object).at(now(clock)).user(user())
                .details(request.details()).outcome(FAILURE).message(request.message())
                .correlationId(CallContext.correlationId().orElse(null)).durationMs(0).build());
    }

    private ActionObject keyed(ActionObject object) {
        return object.lacksKey() ? object.withKey(keys.keyOf(object.type(), object.id()).orElse(null)) : object;
    }

    private String user() {
        return CallContext.systemActor().orElseGet(() -> {
            try {
                return defaultIfBlank(users.name(), SignedInUserPort.UNKNOWN);
            } catch (RuntimeException e) {
                return SignedInUserPort.UNKNOWN;
            }
        });
    }
}
