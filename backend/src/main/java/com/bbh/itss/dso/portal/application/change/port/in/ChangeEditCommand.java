package com.bbh.itss.dso.portal.application.change.port.in;

import com.bbh.itss.dso.portal.domain.change.ChangeSchedule;
import com.bbh.itss.dso.portal.domain.change.ChangeTask;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;

import java.util.List;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

public record ChangeEditCommand(Long version, Long departmentId, String shortDescription, String description,
                                ChangeSchedule schedule, ChangeTemplate template, List<ChangeTask> tasks) {

    public ChangeEditCommand {
        tasks = emptyIfNull(tasks).stream().toList();
    }
}
