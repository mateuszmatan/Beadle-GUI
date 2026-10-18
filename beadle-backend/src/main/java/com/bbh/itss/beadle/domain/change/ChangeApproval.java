package com.bbh.itss.beadle.domain.change;

import com.bbh.itss.beadle.domain.change.ChangeTemplate.Approvers;
import lombok.With;

import java.util.List;
import java.util.stream.Stream;

import static com.bbh.itss.beadle.domain.change.ApprovalState.NOT_APPROVED;
import static com.bbh.itss.beadle.domain.change.ChangeTemplate.GROUP_MAX;
import static com.bbh.itss.beadle.domain.shared.Text.abbreviateBytes;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ChangeApproval(ApprovalRole role, String approver, ApprovalState state, @With Reminder reminder) {

    public ChangeApproval {
        approver = abbreviateBytes(trimToNull(approver), GROUP_MAX);
        state = getIfNull(state, NOT_APPROVED);
        reminder = reminder == null || reminder.sentAt() == null ? null : reminder;
    }

    public static List<ChangeApproval> of(Approvers approvers) {
        return Stream.of(ApprovalRole.values())
                .map(role -> new ChangeApproval(role, role.approverIn(approvers), NOT_APPROVED, null)).toList();
    }

    public boolean awaited() {
        return !state.approved();
    }
}
