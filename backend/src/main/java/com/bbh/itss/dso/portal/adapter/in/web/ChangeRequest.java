package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.change.port.in.ChangeCommand;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record ChangeRequest(
        @NotNull Long productId,
        @Size(max = 50) List<@NotNull Long> serviceIds,
        @Size(max = 50) List<@NotBlank @Size(max = 20) String> epicKeys,
        @Size(max = 200) List<@NotBlank @Size(max = 20) String> storyKeys,
        Instant start,
        Instant end,
        @Size(max = ProductionChange.SHORT_DESCRIPTION_MAX) String shortDescription,
        @Size(max = ProductionChange.DESCRIPTION_MAX) String description) {

    ChangeCommand toCommand() {
        return new ChangeCommand(productId, serviceIds, epicKeys, storyKeys, start, end, shortDescription,
                description);
    }
}
