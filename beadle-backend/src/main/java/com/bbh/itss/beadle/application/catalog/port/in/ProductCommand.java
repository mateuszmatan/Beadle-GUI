package com.bbh.itss.beadle.application.catalog.port.in;

import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.AuditedObject;
import com.bbh.itss.beadle.domain.catalog.ProductDetails;

import static com.bbh.itss.beadle.domain.audit.ObjectType.PRODUCT;

public record ProductCommand(String code, String name, Long businessServiceId, String ownerTeam, String contactEmail,
                             Long version) implements AuditedObject {

    @Override
    public ActionObject auditedObject() {
        return ActionObject.keyed(PRODUCT, code);
    }

    public ProductDetails details() {
        return new ProductDetails(code, name, null, ownerTeam, contactEmail, businessServiceId);
    }
}
