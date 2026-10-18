package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.adapter.Mirrors;
import com.bbh.itss.beadle.adapter.in.web.ChangeProfileRequest.TaskDetailsDto;
import com.bbh.itss.beadle.adapter.in.web.ChangeProfileRequest.TemplateDto;
import com.bbh.itss.beadle.application.change.port.in.ChangeEditCommand;
import com.bbh.itss.beadle.domain.change.ChangeSchedule;
import com.bbh.itss.beadle.domain.change.ChangeTask;
import com.bbh.itss.beadle.domain.change.ChangeTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

import static com.bbh.itss.beadle.adapter.RecordMapper.map;
import static com.bbh.itss.beadle.domain.change.ChangeTemplate.NUMBER_MAX;
import static com.bbh.itss.beadle.domain.change.ProductionChange.DESCRIPTION_MAX;
import static com.bbh.itss.beadle.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX;
import static com.bbh.itss.beadle.domain.change.TaskDetails.MAX_TASKS;

public record ChangeEditRequest(
        @NotNull Long version,
        @NotNull Long businessServiceId,
        @NotBlank @Size(max = SHORT_DESCRIPTION_MAX) String shortDescription,
        @NotBlank @Size(max = DESCRIPTION_MAX) String description,
        @NotNull ChangeSchedule schedule,
        @NotNull @Valid TemplateDto template,
        @NotNull @Size(max = MAX_TASKS) List<@NotNull @Valid TaskDto> tasks) {

    ChangeEditCommand toCommand() {
        return new ChangeEditCommand(version, businessServiceId, shortDescription, description, schedule,
                map(template, ChangeTemplate.class), tasksOf(tasks));
    }

    static List<ChangeTask> tasksOf(List<TaskDto> tasks) {
        return tasks.stream().map(task -> map(task, ChangeTask.class)).toList();
    }

    public record TaskDto(@Size(max = NUMBER_MAX) String number, @NotNull @Valid TaskDetailsDto details,
                          Instant start) implements Mirrors<ChangeTask> {
    }
}
