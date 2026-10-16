package com.bbh.itss.beadle.application.audit.port.out;

import com.bbh.itss.beadle.domain.audit.Action;
import com.bbh.itss.beadle.domain.audit.ActionHistoryPage;
import com.bbh.itss.beadle.domain.audit.ActionHistoryQuery;
import com.bbh.itss.beadle.domain.audit.ActionRecord;

import java.util.List;

/** The stored audit trail. Rows are only ever inserted, in the transaction of the caller. */
public interface ActionHistoryPort {

    ActionRecord record(ActionRecord record);

    ActionHistoryPage find(ActionHistoryQuery query);

    List<String> users();

    List<Action> actions();
}
