package com.bbh.itss.beadle.application.audit;

import com.bbh.itss.beadle.application.CallContext;
import com.bbh.itss.beadle.application.ReadOnly;
import com.bbh.itss.beadle.application.UseCase;
import com.bbh.itss.beadle.application.audit.port.in.ActionHistoryUseCase;
import com.bbh.itss.beadle.application.audit.port.out.ActionHistoryPort;
import com.bbh.itss.beadle.application.audit.port.out.ObjectKeyPort;
import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort;
import com.bbh.itss.beadle.domain.audit.Action;
import com.bbh.itss.beadle.domain.audit.ActionHistoryPage;
import com.bbh.itss.beadle.domain.audit.ActionHistoryQuery;
import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.ActionRecord;
import com.bbh.itss.beadle.domain.audit.ActionRecord.ActionRecordBuilder;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.bbh.itss.beadle.application.user.port.out.SignedInUserPort.UNKNOWN;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

@UseCase
@RequiredArgsConstructor
public class ActionHistoryService implements ActionHistoryUseCase {

    private final ActionHistoryPort history;
    private final ObjectKeyPort keys;
    private final SignedInUserPort users;

    @Override
    public ActionRecord record(ActionRecordBuilder row, ActionObject object) {
        ActionObject keyed = keyed(object);
        return history.record(row.user(actor()).correlationId(CallContext.correlationId().orElse(null))
                .objectType(keyed.type()).objectId(keyed.id()).objectKey(keyed.key()).build());
    }

    @Override
    @ReadOnly
    public ActionObject keyed(ActionObject object) {
        return object.keyedBy(keys::keyOf);
    }

    @Override
    @ReadOnly
    public ActionHistoryPage find(ActionHistoryQuery query) {
        return history.find(query);
    }

    @Override
    @ReadOnly
    public List<String> users() {
        return history.users();
    }

    @Override
    @ReadOnly
    public List<Action> actions() {
        return history.actions();
    }

    private String actor() {
        return CallContext.systemActor().orElseGet(() -> {
            try {
                return defaultIfBlank(users.name(), UNKNOWN);
            } catch (RuntimeException e) {
                return UNKNOWN;
            }
        });
    }
}
