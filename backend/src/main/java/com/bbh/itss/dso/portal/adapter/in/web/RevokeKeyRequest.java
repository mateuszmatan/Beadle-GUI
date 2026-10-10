package com.bbh.itss.dso.portal.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineKey.REVOKE_REASON_MAX;

public record RevokeKeyRequest(@NotBlank(message = "say why the key is invalidated") @Size(max = REVOKE_REASON_MAX)
                               String reason) {
}
