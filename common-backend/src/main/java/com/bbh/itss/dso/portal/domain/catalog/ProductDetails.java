package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ProductDetails(String code, String name, String description, String ownerTeam, String contactEmail,
                             Long departmentId) {

    public static final String CODE_PATTERN = "^[A-Z][A-Z0-9_-]{1,49}$";
    public static final String CODE_RULE = "use 2 to 50 upper case letters, digits, '-' or '_', starting with a letter";
    public static final int NAME_MAX = 200;
    public static final int DESCRIPTION_MAX = 4000;
    public static final int OWNER_TEAM_MAX = 200;

    public ProductDetails {
        code = trim(code);
        name = trim(name);
        description = trimToNull(description);
        ownerTeam = trimToNull(ownerTeam);
        contactEmail = trimToNull(contactEmail);
    }

    public ValidationProblems validate(ValidationProblems problems, ProductDirectory directory) {
        if (isBlank(code)) {
            problems.add("code", "must not be blank");
        }
        if (isBlank(name)) {
            problems.add("name", "must not be blank");
        }
        problems.fits("name", name, NAME_MAX)
                .fits("description", description, DESCRIPTION_MAX)
                .fits("ownerTeam", ownerTeam, OWNER_TEAM_MAX);
        if (departmentId == null) {
            problems.add("departmentId", "choose the product's department");
        } else if (!directory.departmentExists(departmentId)) {
            problems.add("departmentId", "department " + departmentId + " does not exist");
        }
        return problems;
    }

    public void requireUnique(Long productId, ProductDirectory directory) {
        directory.findProductByCode(code)
                .filter(other -> !other.isProduct(productId))
                .ifPresent(other -> {
                    throw new IllegalStateException("Product code " + code + " is already used by " + other.name());
                });
        directory.findProductByName(name)
                .filter(other -> !other.isProduct(productId))
                .ifPresent(other -> {
                    throw new IllegalStateException("A product named " + other.name() + " already exists");
                });
    }
}
