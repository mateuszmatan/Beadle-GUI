package com.bbh.itss.dso.portal.application.change.port.in;

import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import lombok.Builder;

import java.time.Instant;

@Builder
public record ChangeProfileView(long productId, String productName, Long version, Instant updatedAt,
                                ChangeTemplate template) {
}
