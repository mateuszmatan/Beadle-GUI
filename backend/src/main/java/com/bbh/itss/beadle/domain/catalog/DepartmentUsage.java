package com.bbh.itss.beadle.domain.catalog;

import java.util.Optional;

public interface DepartmentUsage {

    long productCount();

    default Optional<String> deletionRefusal(String department) {
        return Optional.empty();
    }
}
