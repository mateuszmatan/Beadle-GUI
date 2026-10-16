package com.bbh.itss.beadle.application.change.port.in;

import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.AuditedObject;
import com.bbh.itss.beadle.domain.change.ChangeTemplate;
import com.bbh.itss.beadle.domain.change.TaskDetails;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

import static com.bbh.itss.beadle.domain.audit.ObjectType.CHANGE_PROFILE;

@Builder
public record ChangeProfileView(long productId, String productName, Long version, Instant updatedAt,
                                ChangeTemplate template, List<TaskDetails> tasks) implements AuditedObject {

    /** The template of a product is filed under the product's id; the audit trail looks up its code. */
    @Override
    public ActionObject auditedObject() {
        return ActionObject.of(CHANGE_PROFILE, productId);
    }
}
