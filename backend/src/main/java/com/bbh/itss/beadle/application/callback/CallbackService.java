package com.bbh.itss.beadle.application.callback;

import com.bbh.itss.beadle.application.Audited;
import com.bbh.itss.beadle.application.UseCase;
import com.bbh.itss.beadle.application.WithoutTransaction;
import com.bbh.itss.beadle.application.callback.port.in.CallbacksUseCase;
import com.bbh.itss.beadle.application.change.port.in.ProductionChangesUseCase;
import com.bbh.itss.beadle.domain.callback.InternalCallback;
import com.bbh.itss.beadle.domain.callback.ProTechCallback;
import com.bbh.itss.beadle.domain.change.ProductionChange;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

import static com.bbh.itss.beadle.domain.audit.Action.CALLBACK_INTERNAL;
import static com.bbh.itss.beadle.domain.audit.Action.CALLBACK_PROTECH;

@UseCase
@RequiredArgsConstructor
public class CallbackService implements CallbacksUseCase {

    private final ProductionChangesUseCase changes;

    @Override
    @WithoutTransaction
    @Audited(CALLBACK_PROTECH)
    public Optional<ProductionChange> proTech(ProTechCallback callback) {
        return changes.resync(callback.number());
    }

    @Override
    @WithoutTransaction
    @Audited(CALLBACK_INTERNAL)
    public void internal(InternalCallback callback) {
        // Beadle keeps only the key of the tickets it created; the audit row of this call records the new status.
    }
}
