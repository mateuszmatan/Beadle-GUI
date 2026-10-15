package com.bbh.itss.beadle.application.catalog.port.in;

import com.bbh.itss.beadle.domain.catalog.DepartmentUsage;

public record DepartmentView(long id, String name, long version, DepartmentUsage usage) {
}
