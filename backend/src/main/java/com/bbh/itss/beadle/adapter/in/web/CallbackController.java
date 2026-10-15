package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.application.CallContext.Scope;
import com.bbh.itss.beadle.application.callback.port.in.CallbacksUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.bbh.itss.beadle.application.CallContext.actingAs;
import static com.bbh.itss.beadle.application.CallContext.correlatedBy;
import static org.springframework.http.HttpStatus.ACCEPTED;

/**
 * Where Beadle-ProTech and Beadle-Internal tell Beadle-GUI that something moved in ProTech or in a system behind
 * Beadle-Internal. Only the service token opens these endpoints; what they cause runs for the calling system,
 * {@code system:protech} or {@code system:internal}, within the correlation id of the user action that started it.
 */
@RestController
@RequestMapping("/api/callbacks")
@RequiredArgsConstructor
public class CallbackController {

    public static final String PROTECH = "system:protech";
    public static final String INTERNAL = "system:internal";

    private final CallbacksUseCase callbacks;

    @PostMapping("/protech")
    @ResponseStatus(ACCEPTED)
    public void proTech(@Valid @RequestBody ProTechCallbackRequest request) {
        try (Scope system = actingAs(PROTECH); Scope action = correlatedBy(request.correlationId())) {
            callbacks.proTech(request.toCallback());
        }
    }

    @PostMapping("/internal")
    @ResponseStatus(ACCEPTED)
    public void internal(@Valid @RequestBody InternalCallbackRequest request) {
        try (Scope system = actingAs(INTERNAL); Scope action = correlatedBy(request.correlationId())) {
            callbacks.internal(request.toCallback());
        }
    }
}
