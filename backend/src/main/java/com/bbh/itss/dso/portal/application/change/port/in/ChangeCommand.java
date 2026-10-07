package com.bbh.itss.dso.portal.application.change.port.in;

import java.time.Instant;
import java.util.List;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

public record ChangeCommand(long productId, List<Long> serviceIds, List<String> epicKeys, List<String> storyKeys,
                            Instant start, Instant end, String shortDescription, String description) {

    public ChangeCommand {
        serviceIds = emptyIfNull(serviceIds).stream().distinct().toList();
        epicKeys = emptyIfNull(epicKeys).stream().distinct().toList();
        storyKeys = emptyIfNull(storyKeys).stream().distinct().toList();
    }
}
