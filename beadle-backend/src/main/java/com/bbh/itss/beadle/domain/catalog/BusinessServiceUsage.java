package com.bbh.itss.beadle.domain.catalog;

import java.util.Optional;

/** What a business service owns: it can be deleted only while it owns no product and no change. */
public record BusinessServiceUsage(long productCount, long changeCount) {

    public static final BusinessServiceUsage UNUSED = new BusinessServiceUsage(0, 0);

    /** Why the business service cannot be deleted; empty when it can. */
    public Optional<String> refusal(String businessService) {
        if (productCount > 0) {
            return Optional.of(businessService + " still has " + productCount
                    + " product(s). Move them to another business service first.");
        }
        return changeCount == 0 ? Optional.empty()
                : Optional.of(businessService + " still owns " + changeCount + " change(s), so it cannot be deleted.");
    }
}
