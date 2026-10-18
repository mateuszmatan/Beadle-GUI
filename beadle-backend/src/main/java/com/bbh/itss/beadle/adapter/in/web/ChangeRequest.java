package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.application.change.port.in.ChangeCommand;
import com.bbh.itss.beadle.domain.change.ChangeSchedule;
import com.bbh.itss.beadle.domain.change.ChangeTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.bbh.itss.beadle.domain.change.ProductionChange.DESCRIPTION_MAX;
import static com.bbh.itss.beadle.domain.change.ProductionChange.FIX_VERSION_MAX;
import static com.bbh.itss.beadle.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX;

public record ChangeRequest(
        @NotNull Long productId,
        @NotBlank @Size(max = FIX_VERSION_MAX) String fixVersion,
        @Size(max = 50) List<@NotBlank @Size(max = 20) String> epicKeys,
        @Size(max = 200) List<@NotBlank @Size(max = 20) String> storyKeys,
        @NotNull ChangeSchedule schedule,
        @NotNull ChangeTemplate template,
        @Size(max = SHORT_DESCRIPTION_MAX) String shortDescription,
        @Size(max = DESCRIPTION_MAX) String description) {

    ChangeCommand toCommand() {
        return new ChangeCommand(productId, fixVersion, epicKeys, storyKeys, schedule, template, shortDescription,
                description);
    }
}
