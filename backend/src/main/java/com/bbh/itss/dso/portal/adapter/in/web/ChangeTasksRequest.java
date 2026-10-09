package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.in.web.ChangeEditRequest.TaskDto;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeTasksCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.bbh.itss.dso.portal.adapter.in.web.ChangeEditRequest.tasksOf;
import static com.bbh.itss.dso.portal.domain.change.TaskDetails.MAX_TASKS;

public record ChangeTasksRequest(
        @NotNull Long version,
        @NotNull Long departmentId,
        @NotNull @Size(min = 1, max = MAX_TASKS) List<@NotNull @Valid TaskDto> tasks) {

    ChangeTasksCommand toCommand() {
        return new ChangeTasksCommand(version, departmentId, tasksOf(tasks));
    }
}
