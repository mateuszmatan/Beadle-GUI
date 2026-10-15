package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.domain.callback.ProTechCallback;
import com.bbh.itss.beadle.domain.callback.ProTechEvent;
import com.bbh.itss.beadle.domain.change.ChangeState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

import static org.apache.commons.lang3.StringUtils.trim;

public record ProTechCallbackRequest(@NotBlank @Size(max = 40) String number, @NotNull ProTechEvent event,
                                     ChangeState state, Instant at, @Size(max = 64) String correlationId) {

    ProTechCallback toCallback() {
        return new ProTechCallback(trim(number), event, state, at, trim(correlationId));
    }
}
