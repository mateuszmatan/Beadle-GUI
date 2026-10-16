package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.domain.callback.InternalCallback;
import com.bbh.itss.beadle.domain.callback.InternalSystem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

import static org.apache.commons.lang3.StringUtils.trim;

public record InternalCallbackRequest(@NotNull InternalSystem system, @NotBlank @Size(max = 100) String key,
                                      @Size(max = 100) String status, Instant at,
                                      @Size(max = 64) String correlationId) {

    InternalCallback toCallback() {
        return new InternalCallback(system, trim(key), trim(status), at, trim(correlationId));
    }
}
