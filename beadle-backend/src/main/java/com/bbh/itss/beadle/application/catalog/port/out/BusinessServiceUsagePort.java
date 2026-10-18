package com.bbh.itss.beadle.application.catalog.port.out;

import com.bbh.itss.beadle.domain.catalog.BusinessServiceUsage;

import java.util.Map;

public interface BusinessServiceUsagePort {

    /** The usage of each business service that owns a product or a change, by its id. */
    Map<Long, BusinessServiceUsage> perBusinessService();
}
