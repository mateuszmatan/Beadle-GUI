package com.bbh.itss.beadle.adapter.out.persistence;

import com.bbh.itss.beadle.application.catalog.port.out.BusinessServiceUsagePort;
import com.bbh.itss.beadle.application.catalog.port.out.DepartmentUsagePort;
import com.bbh.itss.beadle.domain.catalog.BusinessServiceUsage;
import com.bbh.itss.beadle.domain.catalog.DepartmentUsage;
import com.bbh.itss.beadle.domain.change.ChangeUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.bbh.itss.beadle.adapter.out.persistence.Counts.perId;
import static com.bbh.itss.beadle.domain.change.ChangeUsage.UNUSED;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@Component
@RequiredArgsConstructor
class ChangeUsageAdapter implements DepartmentUsagePort, BusinessServiceUsagePort {

    private final ProductJpaRepository products;
    private final ProductionChangeJpaRepository changes;

    @Override
    public Map<Long, DepartmentUsage> perDepartment() {
        Map<Long, Long> productCounts = perId(products.countByDepartment());
        Map<Long, Long> changeCounts = perId(changes.countByDepartment());
        Set<Long> departments = new HashSet<>(productCounts.keySet());
        departments.addAll(changeCounts.keySet());
        return departments.stream().collect(toMap(identity(), department -> new ChangeUsage(
                productCounts.getOrDefault(department, 0L), changeCounts.getOrDefault(department, 0L))));
    }

    @Override
    public Map<Long, BusinessServiceUsage> perBusinessService() {
        return new HashMap<>(perDepartment());
    }

    @Override
    public DepartmentUsage unused() {
        return UNUSED;
    }
}
