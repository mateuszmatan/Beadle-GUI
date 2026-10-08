package com.bbh.itss.dso.portal.application.catalog.port.in;

public record DepartmentView(long id, String name, long version, long productCount, long serviceCount,
                             long pipelineCount, long activePipelineCount, long changeCount) {
}
