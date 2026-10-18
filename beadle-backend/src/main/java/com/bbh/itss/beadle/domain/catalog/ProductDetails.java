package com.bbh.itss.beadle.domain.catalog;

import com.bbh.itss.beadle.domain.shared.ValidationProblems;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ProductDetails(String code, String name, String ownerTeam, String contactEmail, Long businessServiceId) {

    public static final String CODE_PATTERN = "^[A-Z][A-Z0-9_-]{1,49}$";
    public static final String CODE_RULE = "use 2 to 50 upper case letters, digits, '-' or '_', starting with a letter";
    public static final int NAME_MAX = 200;
    public static final int OWNER_TEAM_MAX = 200;

    public ProductDetails {
        code = trim(code);
        name = trim(name);
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
        problems.fits("name", name, NAME_MAX).fits("ownerTeam", ownerTeam, OWNER_TEAM_MAX);
        if (businessServiceId == null) {
            problems.add("businessServiceId", "choose the product's business service");
        } else if (!directory.businessServiceExists(businessServiceId)) {
            problems.add("businessServiceId", "business service " + businessServiceId + " does not exist");
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
