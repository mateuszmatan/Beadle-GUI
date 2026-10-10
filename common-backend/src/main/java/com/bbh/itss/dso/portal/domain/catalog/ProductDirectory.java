package com.bbh.itss.dso.portal.domain.catalog;

import java.util.Optional;

public interface ProductDirectory {

    Optional<ProductIdentity> findProductByCode(String code);

    Optional<ProductIdentity> findProductByName(String name);

    boolean departmentExists(long id);

    record ProductIdentity(long id, String name) {

        boolean isProduct(Long productId) {
            return productId != null && productId == id;
        }
    }
}
