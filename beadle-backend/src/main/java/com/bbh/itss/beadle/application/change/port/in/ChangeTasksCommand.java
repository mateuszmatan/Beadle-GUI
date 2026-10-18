package com.bbh.itss.beadle.application.change.port.in;

import com.bbh.itss.beadle.domain.change.ChangeTask;

import java.util.List;

import static java.util.Objects.requireNonNullElse;

public record ChangeTasksCommand(Long version, Long businessServiceId, List<ChangeTask> tasks) {

    public ChangeTasksCommand {
        tasks = requireNonNullElse(tasks, List.<ChangeTask>of()).stream().toList();
    }
}
