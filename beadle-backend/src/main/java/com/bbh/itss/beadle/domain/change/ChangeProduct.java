package com.bbh.itss.beadle.domain.change;

import static com.bbh.itss.beadle.domain.change.ChangeTemplate.jiraKeyOf;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;

public record ChangeProduct(long id, String code, String name, String ownerTeam,
                            Long businessServiceId, String businessServiceName, String jiraProjectKey) {

    public String jiraProject() {
        return getIfNull(jiraProjectKey, () -> jiraKeyOf(code));
    }
}
