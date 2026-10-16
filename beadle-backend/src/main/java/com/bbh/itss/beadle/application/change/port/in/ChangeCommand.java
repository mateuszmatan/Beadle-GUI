package com.bbh.itss.beadle.application.change.port.in;

import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.AuditedObject;
import com.bbh.itss.beadle.domain.change.ChangeSchedule;
import com.bbh.itss.beadle.domain.change.ChangeTemplate;

import java.util.List;

import static com.bbh.itss.beadle.domain.audit.ObjectType.PRODUCT;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ChangeCommand(long productId, String fixVersion, List<String> epicKeys, List<String> storyKeys,
                            ChangeSchedule schedule, ChangeTemplate template, String shortDescription,
                            String description) implements AuditedObject {

    public ChangeCommand {
        fixVersion = trimToNull(fixVersion);
        epicKeys = emptyIfNull(epicKeys).stream().distinct().toList();
        storyKeys = emptyIfNull(storyKeys).stream().distinct().toList();
    }

    /** A change not raised yet is filed under the product it is drafted for; the audit trail looks up its code. */
    @Override
    public ActionObject auditedObject() {
        return ActionObject.of(PRODUCT, productId);
    }
}
