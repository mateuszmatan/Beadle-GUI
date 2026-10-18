package com.bbh.itss.beadle.domain.change;

import com.bbh.itss.beadle.domain.change.ChangeTemplate.Approvers;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;

@RequiredArgsConstructor
public enum ApprovalRole {
    BUSINESS("business approver", Approvers::businessApprover),
    L1("L1 approver", Approvers::l1Manager),
    L2("L2 approver", Approvers::l2Manager),
    SUPPORT("support approver", Approvers::supportApprover);

    @Getter
    private final String label;
    private final Function<Approvers, String> named;

    public String approverIn(Approvers approvers) {
        return named.apply(approvers);
    }
}
