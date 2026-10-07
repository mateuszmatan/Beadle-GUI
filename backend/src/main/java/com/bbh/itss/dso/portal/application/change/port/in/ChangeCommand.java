package com.bbh.itss.dso.portal.application.change.port.in;

import com.bbh.itss.dso.portal.domain.change.ChangeSchedule;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;

import java.util.List;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ChangeCommand(long productId, List<Long> serviceIds, String fixVersion, List<String> epicKeys,
                            List<String> storyKeys, ChangeSchedule schedule, ChangeTemplate template,
                            String shortDescription, String description) {

    public ChangeCommand {
        serviceIds = emptyIfNull(serviceIds).stream().distinct().toList();
        fixVersion = trimToNull(fixVersion);
        epicKeys = emptyIfNull(epicKeys).stream().distinct().toList();
        storyKeys = emptyIfNull(storyKeys).stream().distinct().toList();
    }
}
