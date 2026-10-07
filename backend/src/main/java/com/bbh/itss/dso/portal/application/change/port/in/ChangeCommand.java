package com.bbh.itss.dso.portal.application.change.port.in;

import java.time.Instant;
import java.util.List;

public record ChangeCommand(long productId, List<Long> serviceIds, List<String> epicKeys, List<String> storyKeys,
                            Instant start, Instant end, String shortDescription, String description) {

    public ChangeCommand {
        serviceIds = serviceIds == null ? List.of() : serviceIds.stream().distinct().toList();
        epicKeys = epicKeys == null ? List.of() : epicKeys.stream().distinct().toList();
        storyKeys = storyKeys == null ? List.of() : storyKeys.stream().distinct().toList();
    }
}
