package com.bbh.itss.beadle.application.audit.port.in;

import com.bbh.itss.beadle.domain.audit.Action;
import com.bbh.itss.beadle.domain.audit.ActionHistoryPage;
import com.bbh.itss.beadle.domain.audit.ActionHistoryQuery;
import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.ActionRecord;
import com.bbh.itss.beadle.domain.audit.ActionRecord.ActionRecordBuilder;

import java.util.List;

/**
 * The audit trail: written by the audit interceptor around the audited use cases and for the requests the API refuses
 * before their use case runs, and read by the Action History.
 */
public interface ActionHistoryUseCase {

    /**
     * Writes the row about the object, by the system the call is made for, else the signed-in user, under the
     * correlation id of the call; an object known by its id only gets the key of the stored object.
     */
    ActionRecord record(ActionRecordBuilder row, ActionObject object);

    /** The rows that match the query, newest first. */
    ActionHistoryPage find(ActionHistoryQuery query);

    /** Every user name the audit trail holds, for the user filter. */
    List<String> users();

    /** Every action the audit trail holds, for the action filter. */
    List<Action> actions();
}
