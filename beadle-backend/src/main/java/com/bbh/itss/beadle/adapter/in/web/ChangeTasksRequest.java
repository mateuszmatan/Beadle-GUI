package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.adapter.Mirrors;
import com.bbh.itss.beadle.adapter.in.web.ChangeEditRequest.TaskDto;
import com.bbh.itss.beadle.application.change.port.in.ChangeTasksCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.bbh.itss.beadle.domain.change.TaskDetails.MAX_TASKS;

public record ChangeTasksRequest(
        @NotNull Long version,
        @NotNull Long businessServiceId,
        @NotNull @Size(min = 1, max = MAX_TASKS) List<@NotNull @Valid TaskDto> tasks)
        implements Mirrors<ChangeTasksCommand> {
}
