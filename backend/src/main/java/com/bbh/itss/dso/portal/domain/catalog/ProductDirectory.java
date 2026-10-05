package com.bbh.itss.dso.portal.domain.catalog;

import java.util.List;
import java.util.Optional;

public interface ProductDirectory {

    Optional<ProductIdentity> findProductByCode(String code);

    Optional<ProductIdentity> findProductByName(String name);

    List<ServiceIdentity> findServicesByMetricsTags(String influxProject, String influxEnv);

    List<ServiceIdentity> findServicesBySonarProjectKey(String projectKey);

    record ProductIdentity(long id, String name) {
    }

    record ServiceIdentity(long id, String productName, String name) {

        public String describe() {
            return productName + " / " + name;
        }
    }
}
