package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.time.Instant;

import static com.bbh.itss.dso.portal.domain.shared.Versions.requireCurrent;
import static java.util.Objects.requireNonNull;

public record Product(Long id, ProductDetails details, long version, Instant updatedAt) {

    public static Product create(ProductDetails details, ProductDirectory directory) {
        return new Product(null, checked(null, details, directory), 0, null);
    }

    public Product change(Long expectedVersion, ProductDetails changed, ProductDirectory directory) {
        requireCurrent(expectedVersion, version);
        ProductDetails kept = new ProductDetails(details.code(), changed.name(), null, changed.ownerTeam(),
                changed.contactEmail(), changed.departmentId());
        return new Product(id, checked(id, kept, directory), version, updatedAt);
    }

    private static ProductDetails checked(Long id, ProductDetails details, ProductDirectory directory) {
        requireNonNull(details, "a product needs its details");
        details.validate(new ValidationProblems(), directory).throwIfAny();
        details.requireUnique(id, directory);
        return details;
    }
}
