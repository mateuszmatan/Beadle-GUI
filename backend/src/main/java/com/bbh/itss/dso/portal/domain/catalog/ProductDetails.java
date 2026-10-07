package com.bbh.itss.dso.portal.domain.catalog;

import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ProductDetails(String code, String name, String description, String ownerTeam, String contactEmail,
                             Long departmentId) {

    public ProductDetails {
        code = trim(code);
        name = trim(name);
        description = trimToNull(description);
        ownerTeam = trimToNull(ownerTeam);
        contactEmail = trimToNull(contactEmail);
    }
}
