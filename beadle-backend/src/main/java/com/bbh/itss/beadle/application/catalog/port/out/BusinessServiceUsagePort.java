package com.bbh.itss.beadle.application.catalog.port.out;

import com.bbh.itss.beadle.domain.catalog.BusinessServiceUsage;

import java.util.Map;

public interface BusinessServiceUsagePort {

    Map<Long, BusinessServiceUsage> perBusinessService();

    BusinessServiceUsage unused();
}
