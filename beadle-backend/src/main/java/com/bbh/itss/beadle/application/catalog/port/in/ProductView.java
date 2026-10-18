package com.bbh.itss.beadle.application.catalog.port.in;

import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.AuditedObject;

import java.time.Instant;

import static com.bbh.itss.beadle.domain.audit.ObjectType.PRODUCT;

public record ProductView(long id, String code, String name, String ownerTeam, String contactEmail,
                          Long businessServiceId, String businessServiceName, long version, Instant updatedAt)
        implements AuditedObject {

    @Override
    public ActionObject auditedObject() {
        return new ActionObject(PRODUCT, id, code);
    }
}
