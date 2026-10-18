package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.adapter.Mirrors;
import com.bbh.itss.beadle.domain.callback.InternalCallback;
import com.bbh.itss.beadle.domain.callback.InternalSystem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record InternalCallbackRequest(@NotNull InternalSystem system, @NotBlank @Size(max = 100) String key,
                                      @Size(max = 100) String status, Instant at)
        implements Mirrors<InternalCallback> {
}
