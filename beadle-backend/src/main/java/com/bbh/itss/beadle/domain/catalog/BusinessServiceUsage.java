package com.bbh.itss.beadle.domain.catalog;

import java.util.Optional;

public interface BusinessServiceUsage {

    long productCount();

    default Optional<String> deletionRefusal(String businessService) {
        return Optional.empty();
    }
}
