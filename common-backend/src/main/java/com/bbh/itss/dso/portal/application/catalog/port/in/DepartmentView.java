package com.bbh.itss.dso.portal.application.catalog.port.in;

import com.bbh.itss.dso.portal.domain.catalog.DepartmentUsage;

public record DepartmentView(long id, String name, long version, DepartmentUsage usage) {
}
