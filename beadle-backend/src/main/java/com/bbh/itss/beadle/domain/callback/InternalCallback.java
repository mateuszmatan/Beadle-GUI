package com.bbh.itss.beadle.domain.callback;

import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.AuditedObject;

import java.time.Instant;

import static com.bbh.itss.beadle.domain.audit.ObjectType.TICKET;

/**
 * Beadle-Internal telling Beadle-GUI that an item Beadle created in one of its systems changed status, such as the
 * CyberTrack secure coding ticket {@code SCP-123} moving to Done.
 */
public record InternalCallback(InternalSystem system, String key, String status, Instant at, String correlationId)
        implements AuditedObject {

    @Override
    public ActionObject auditedObject() {
        return ActionObject.keyed(TICKET, key);
    }
}
