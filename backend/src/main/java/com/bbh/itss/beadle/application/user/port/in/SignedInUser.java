package com.bbh.itss.beadle.application.user.port.in;

import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.AuditedObject;

import static com.bbh.itss.beadle.domain.audit.ObjectType.USER;

public record SignedInUser(String name) implements AuditedObject {

    @Override
    public ActionObject auditedObject() {
        return ActionObject.keyed(USER, name);
    }
}
