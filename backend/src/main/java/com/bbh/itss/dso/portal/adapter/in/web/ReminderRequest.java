package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.change.port.in.ReminderCommand;
import com.bbh.itss.dso.portal.domain.change.ApprovalRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.NUMBER_MAX;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ReminderRequest(@NotNull Long departmentId, ApprovalRole approval, @Size(max = NUMBER_MAX) String task) {

    ReminderCommand toCommand() {
        return new ReminderCommand(departmentId, approval, trimToNull(task));
    }
}
