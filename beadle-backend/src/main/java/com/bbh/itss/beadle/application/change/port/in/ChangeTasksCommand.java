package com.bbh.itss.beadle.application.change.port.in;

import com.bbh.itss.beadle.domain.change.ChangeTask;

import java.util.List;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

public record ChangeTasksCommand(Long version, Long businessServiceId, List<ChangeTask> tasks) {

    public ChangeTasksCommand {
        tasks = emptyIfNull(tasks).stream().toList();
    }
}
