package com.bbh.itss.beadle.application.audit.port.in;

import com.bbh.itss.beadle.domain.audit.Action;
import com.bbh.itss.beadle.domain.audit.ActionHistoryPage;
import com.bbh.itss.beadle.domain.audit.ActionHistoryQuery;

import java.util.List;

/** Reads the audit trail; the rows are written by the audit interceptor around the audited use cases. */
public interface ActionHistoryUseCase {

    /** The rows that match the query, newest first. */
    ActionHistoryPage find(ActionHistoryQuery query);

    /** Every user name the audit trail holds, for the user filter. */
    List<String> users();

    /** Every action the audit trail holds, for the action filter. */
    List<Action> actions();
}
