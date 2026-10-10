package com.bbh.itss.dso.portal.application.catalog.port.out;

import com.bbh.itss.dso.portal.domain.catalog.DepartmentUsage;

import java.util.Map;

public interface DepartmentUsagePort {

    Map<Long, DepartmentUsage> perDepartment();

    DepartmentUsage unused();
}
