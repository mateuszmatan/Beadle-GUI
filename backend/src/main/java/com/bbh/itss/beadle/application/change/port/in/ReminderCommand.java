package com.bbh.itss.beadle.application.change.port.in;

import com.bbh.itss.beadle.domain.change.ApprovalRole;

public record ReminderCommand(Long departmentId, ApprovalRole approval, String task) {
}
