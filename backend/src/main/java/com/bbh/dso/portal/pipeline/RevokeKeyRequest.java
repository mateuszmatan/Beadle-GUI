package com.bbh.dso.portal.pipeline;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeKeyRequest(@NotBlank(message = "say why the key is invalidated") @Size(max = 500) String reason) {
}
