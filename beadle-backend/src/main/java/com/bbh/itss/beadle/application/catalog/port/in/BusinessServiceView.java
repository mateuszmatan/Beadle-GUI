package com.bbh.itss.beadle.application.catalog.port.in;

import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.AuditedObject;
import com.bbh.itss.beadle.domain.catalog.BusinessServiceUsage;

import static com.bbh.itss.beadle.domain.audit.ObjectType.BUSINESS_SERVICE;

public record BusinessServiceView(long id, String name, long version, BusinessServiceUsage usage) implements AuditedObject {

    @Override
    public ActionObject auditedObject() {
        return new ActionObject(BUSINESS_SERVICE, id, name);
    }
}
