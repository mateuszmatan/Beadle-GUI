package com.bbh.itss.beadle.domain.change;

import com.bbh.itss.beadle.domain.catalog.DepartmentUsage;

import java.util.Optional;

public record ChangeUsage(long productCount, long changeCount) implements DepartmentUsage {

    public static final ChangeUsage UNUSED = new ChangeUsage(0, 0);

    @Override
    public Optional<String> deletionRefusal(String department) {
        return changeCount == 0 ? Optional.empty()
                : Optional.of(department + " still owns " + changeCount + " change(s), so it cannot be deleted.");
    }
}
