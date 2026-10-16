package com.bbh.itss.beadle.application.catalog.port.in;

import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.AuditedObject;
import com.bbh.itss.beadle.domain.catalog.DepartmentUsage;

import static com.bbh.itss.beadle.domain.audit.ObjectType.DEPARTMENT;

public record DepartmentView(long id, String name, long version, DepartmentUsage usage) implements AuditedObject {

    @Override
    public ActionObject auditedObject() {
        return new ActionObject(DEPARTMENT, id, name);
    }
}
