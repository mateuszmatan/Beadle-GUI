package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.Text;

public record ProductDetails(String code, String name, String description, String ownerTeam, String contactEmail,
                             Long departmentId) {

    public ProductDetails {
        code = code == null ? null : code.trim();
        name = name == null ? null : name.trim();
        description = Text.trimToNull(description);
        ownerTeam = Text.trimToNull(ownerTeam);
        contactEmail = Text.trimToNull(contactEmail);
    }
}
