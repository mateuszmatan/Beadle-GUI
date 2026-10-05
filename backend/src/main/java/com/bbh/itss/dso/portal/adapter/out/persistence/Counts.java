package com.bbh.itss.dso.portal.adapter.out.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Counts {

    private Counts() {
    }

    static Map<Long, Long> perProduct(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        rows.forEach(row -> counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));
        return counts;
    }
}
