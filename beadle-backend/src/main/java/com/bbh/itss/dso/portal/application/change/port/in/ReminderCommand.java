package com.bbh.itss.dso.portal.application.change.port.in;

import com.bbh.itss.dso.portal.domain.change.ApprovalRole;

public record ReminderCommand(Long departmentId, ApprovalRole approval, String task) {
}
