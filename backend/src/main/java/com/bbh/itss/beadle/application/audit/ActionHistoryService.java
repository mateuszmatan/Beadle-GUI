package com.bbh.itss.beadle.application.audit;

import com.bbh.itss.beadle.application.ReadOnly;
import com.bbh.itss.beadle.application.UseCase;
import com.bbh.itss.beadle.application.audit.port.in.ActionHistoryUseCase;
import com.bbh.itss.beadle.application.audit.port.out.ActionHistoryPort;
import com.bbh.itss.beadle.domain.audit.Action;
import com.bbh.itss.beadle.domain.audit.ActionHistoryPage;
import com.bbh.itss.beadle.domain.audit.ActionHistoryQuery;
import lombok.RequiredArgsConstructor;

import java.util.List;

@UseCase
@RequiredArgsConstructor
public class ActionHistoryService implements ActionHistoryUseCase {

    private final ActionHistoryPort history;

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
}
