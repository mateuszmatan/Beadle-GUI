package com.bbh.itss.beadle.domain.change;

import com.bbh.itss.beadle.domain.catalog.BusinessServiceUsage;

import java.util.Optional;

public record ChangeUsage(long productCount, long changeCount) implements BusinessServiceUsage {

    public static final ChangeUsage UNUSED = new ChangeUsage(0, 0);

    @Override
    public Optional<String> deletionRefusal(String businessService) {
        return changeCount == 0 ? Optional.empty()
                : Optional.of(businessService + " still owns " + changeCount + " change(s), so it cannot be deleted.");
    }
}
