package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.in.web.ChangeProfileRequest.TaskTextDto;
import com.bbh.itss.dso.portal.adapter.in.web.ChangeProfileRequest.TemplateDto;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeCommand;
import com.bbh.itss.dso.portal.domain.change.ChangeSchedule;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.bbh.itss.dso.portal.adapter.RecordMapper.map;
import static com.bbh.itss.dso.portal.adapter.in.web.ChangeProfileRequest.tasksOf;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.FIX_VERSION_MAX;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.change.TaskText.MAX_TASKS;

public record ChangeRequest(
        @NotNull Long productId,
        @NotBlank @Size(max = FIX_VERSION_MAX) String fixVersion,
        @Size(max = 50) List<@NotBlank @Size(max = 20) String> epicKeys,
        @Size(max = 200) List<@NotBlank @Size(max = 20) String> storyKeys,
        @NotNull ChangeSchedule schedule,
        @NotNull @Valid TemplateDto template,
        @NotNull @Size(min = 1, max = MAX_TASKS) List<@NotNull @Valid TaskTextDto> tasks,
        @Size(max = SHORT_DESCRIPTION_MAX) String shortDescription,
        @Size(max = DESCRIPTION_MAX) String description) {

    ChangeCommand toCommand() {
        return new ChangeCommand(productId, fixVersion, epicKeys, storyKeys, schedule,
                map(template, ChangeTemplate.class), tasksOf(tasks), shortDescription, description);
    }
}
