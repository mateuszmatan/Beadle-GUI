package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.application.catalog.port.in.BusinessServiceView;
import com.bbh.itss.beadle.domain.catalog.BusinessServiceUsage;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record BusinessServiceResponse(long id, String name, long version, @JsonUnwrapped BusinessServiceUsage usage) {

    public static BusinessServiceResponse of(BusinessServiceView view) {
        return new BusinessServiceResponse(view.id(), view.name(), view.version(), view.usage());
    }
}
