package com.bbh.itss.beadle.domain.callback;

import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.AuditedObject;
import com.bbh.itss.beadle.domain.change.ChangeState;

import java.time.Instant;

import static com.bbh.itss.beadle.domain.audit.ObjectType.CHANGE;

/**
 * Beadle-ProTech telling Beadle-GUI that ProTech moved a change: its state, an approval or a change task. Beadle-GUI
 * then reads the change again from ProTech.
 */
public record ProTechCallback(String number, ProTechEvent event, ChangeState state, Instant at,
                              String correlationId) implements AuditedObject {

    @Override
    public ActionObject auditedObject() {
        return ActionObject.keyed(CHANGE, number);
    }
}
