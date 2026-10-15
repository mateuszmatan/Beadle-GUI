package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentView;
import com.bbh.itss.dso.portal.domain.catalog.DepartmentUsage;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record DepartmentResponse(long id, String name, long version, @JsonUnwrapped DepartmentUsage usage) {

    static DepartmentResponse of(DepartmentView view) {
        return new DepartmentResponse(view.id(), view.name(), view.version(), view.usage());
    }
}
