package com.bbh.itss.dso.portal.application.change.port.in;

import com.bbh.itss.dso.portal.domain.change.ChangeSchedule;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;

import java.util.List;

import static com.bbh.itss.dso.portal.domain.shared.Text.trimToNull;

public record ChangeCommand(long productId, List<Long> serviceIds, String fixVersion, List<String> epicKeys,
                            List<String> storyKeys, ChangeSchedule schedule, ChangeTemplate template,
                            String shortDescription, String description) {

    public ChangeCommand {
        serviceIds = serviceIds == null ? List.of() : serviceIds.stream().distinct().toList();
        fixVersion = trimToNull(fixVersion);
        epicKeys = epicKeys == null ? List.of() : epicKeys.stream().distinct().toList();
        storyKeys = storyKeys == null ? List.of() : storyKeys.stream().distinct().toList();
    }
}
