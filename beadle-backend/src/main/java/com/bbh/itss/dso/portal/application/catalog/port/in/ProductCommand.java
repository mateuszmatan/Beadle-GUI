package com.bbh.itss.dso.portal.application.catalog.port.in;

import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;

public record ProductCommand(String code, String name, Long departmentId, String ownerTeam, String contactEmail,
                             Long version) {

    public ProductDetails details() {
        return new ProductDetails(code, name, null, ownerTeam, contactEmail, departmentId);
    }
}
