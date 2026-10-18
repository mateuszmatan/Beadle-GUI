package com.bbh.itss.beadle.application.change.port.in;

import com.bbh.itss.beadle.domain.change.ChangeSchedule;
import com.bbh.itss.beadle.domain.change.ChangeTask;
import com.bbh.itss.beadle.domain.change.ChangeTemplate;

import java.util.List;

import static java.util.Objects.requireNonNullElse;

public record ChangeEditCommand(Long version, Long businessServiceId, String shortDescription, String description,
                                ChangeSchedule schedule, ChangeTemplate template, List<ChangeTask> tasks) {

    public ChangeEditCommand {
        tasks = requireNonNullElse(tasks, List.<ChangeTask>of()).stream().toList();
    }
}
