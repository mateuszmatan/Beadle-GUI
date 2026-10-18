package com.bbh.itss.beadle.adapter.out.persistence;

import com.bbh.itss.beadle.application.catalog.port.out.BusinessServiceUsagePort;
import com.bbh.itss.beadle.domain.catalog.BusinessServiceUsage;
import com.bbh.itss.beadle.domain.change.ChangeUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bbh.itss.beadle.domain.change.ChangeUsage.UNUSED;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@Component
@RequiredArgsConstructor
class ChangeUsageAdapter implements BusinessServiceUsagePort {

    private final ProductJpaRepository products;
    private final ProductionChangeJpaRepository changes;

    @Override
    public Map<Long, BusinessServiceUsage> perBusinessService() {
        Map<Long, Long> productCounts = perId(products.countByBusinessService());
        Map<Long, Long> changeCounts = perId(changes.countByBusinessService());
        Set<Long> businessServices = new HashSet<>(productCounts.keySet());
        businessServices.addAll(changeCounts.keySet());
        return businessServices.stream().collect(toMap(identity(), id -> new ChangeUsage(
                productCounts.getOrDefault(id, 0L), changeCounts.getOrDefault(id, 0L))));
    }

    @Override
    public BusinessServiceUsage unused() {
        return UNUSED;
    }

    /** The rows {@code [business service id, count]} of a count query by business service. */
    private static Map<Long, Long> perId(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        rows.forEach(row -> counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));
        return counts;
    }
}
