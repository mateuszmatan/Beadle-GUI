package com.bbh.itss.dso.portal.application.change.port.in;

import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import com.bbh.itss.dso.portal.domain.change.TaskDetails;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record ChangeProfileView(long productId, String productName, Long version, Instant updatedAt,
                                ChangeTemplate template, List<TaskDetails> tasks) {
}
