package com.bbh.itss.beadle.domain.catalog;

import java.util.Optional;

public interface ProductDirectory {

    Optional<ProductIdentity> findProductByCode(String code);

    Optional<ProductIdentity> findProductByName(String name);

    boolean businessServiceExists(long id);

    default boolean departmentExists(long id) {
        return businessServiceExists(id);
    }

    record ProductIdentity(long id, String name) {

        boolean isProduct(Long productId) {
            return productId != null && productId == id;
        }
    }
}
