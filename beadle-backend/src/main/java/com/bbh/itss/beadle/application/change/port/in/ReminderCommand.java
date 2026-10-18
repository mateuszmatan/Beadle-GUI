package com.bbh.itss.beadle.application.change.port.in;

import com.bbh.itss.beadle.domain.change.ApprovalRole;

public record ReminderCommand(Long businessServiceId, ApprovalRole approval, String task) {
}
