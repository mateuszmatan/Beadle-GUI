package com.bbh.itss.beadle.application.catalog.port.out;

import com.bbh.itss.beadle.domain.catalog.DepartmentUsage;

import java.util.Map;

public interface DepartmentUsagePort {

    Map<Long, DepartmentUsage> perDepartment();

    DepartmentUsage unused();
}
