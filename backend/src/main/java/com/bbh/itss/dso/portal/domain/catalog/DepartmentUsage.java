package com.bbh.itss.dso.portal.domain.catalog;

import java.util.Optional;

public interface DepartmentUsage {

    long productCount();

    default Optional<String> deletionRefusal(String department) {
        return Optional.empty();
    }
}
