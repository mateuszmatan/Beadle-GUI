package com.bbh.itss.dso.portal.application.change.port.in;

import com.bbh.itss.dso.portal.domain.change.ChangeTask;

import java.util.List;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

public record ChangeTasksCommand(Long version, Long departmentId, List<ChangeTask> tasks) {

    public ChangeTasksCommand {
        tasks = emptyIfNull(tasks).stream().toList();
    }
}
