package com.bbh.itss.beadle.domain.callback;

import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.AuditedObject;

import java.time.Instant;

import static com.bbh.itss.beadle.domain.audit.ObjectType.TICKET;
import static org.apache.commons.lang3.StringUtils.trim;

/**
 * Beadle-Internal telling Beadle-GUI that an item Beadle created in one of its systems changed status, such as the
 * CyberTrack secure coding ticket {@code SCP-123} moving to Done.
 */
public record InternalCallback(InternalSystem system, String key, String status, Instant at)
        implements AuditedObject {

    public InternalCallback {
        key = trim(key);
        status = trim(status);
    }

    @Override
    public ActionObject auditedObject() {
        return ActionObject.keyed(TICKET, key);
    }
}
