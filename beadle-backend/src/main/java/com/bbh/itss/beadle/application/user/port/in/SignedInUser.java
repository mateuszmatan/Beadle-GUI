package com.bbh.itss.beadle.application.user.port.in;

import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.AuditedObject;

import static com.bbh.itss.beadle.domain.audit.ObjectType.USER;

/** The signed-in user as {@code GET /api/me} answers it; {@code email} is {@code null} when it is not known. */
public record SignedInUser(String name, String email) implements AuditedObject {

    @Override
    public ActionObject auditedObject() {
        return ActionObject.keyed(USER, name);
    }
}
