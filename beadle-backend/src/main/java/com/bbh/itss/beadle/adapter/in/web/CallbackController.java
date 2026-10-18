package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.application.callback.port.in.CallbacksUseCase;
import com.bbh.itss.beadle.domain.callback.InternalCallback;
import com.bbh.itss.beadle.domain.callback.ProTechCallback;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.bbh.itss.beadle.adapter.RecordMapper.map;
import static com.bbh.itss.beadle.domain.audit.Action.CALLBACK_INTERNAL;
import static com.bbh.itss.beadle.domain.audit.Action.CALLBACK_PROTECH;
import static org.springframework.http.HttpStatus.ACCEPTED;

/**
 * Where Beadle-ProTech and Beadle-Internal tell Beadle-GUI that something moved in ProTech or in a system behind
 * Beadle-Internal. Only the service token opens these endpoints ({@link ServiceTokenFilter}); what they cause runs for
 * the calling system, {@code system:protech} or {@code system:internal}, within the correlation id of the user action
 * that started it, which the callback brings in {@code X-Correlation-Id} ({@link CorrelationIdFilter}).
 */
@RestController
@RequestMapping("/api/callbacks")
@RequiredArgsConstructor
public class CallbackController {

    private final CallbacksUseCase callbacks;

    @PostMapping("/protech")
    @AuditedRequest(value = CALLBACK_PROTECH, objectKey = "number")
    @ResponseStatus(ACCEPTED)
    public void proTech(@Valid @RequestBody ProTechCallbackRequest request) {
        callbacks.proTech(map(request, ProTechCallback.class));
    }

    @PostMapping("/internal")
    @AuditedRequest(value = CALLBACK_INTERNAL, objectKey = "key")
    @ResponseStatus(ACCEPTED)
    public void internal(@Valid @RequestBody InternalCallbackRequest request) {
        callbacks.internal(map(request, InternalCallback.class));
    }
}
