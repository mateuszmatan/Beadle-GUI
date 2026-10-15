package com.bbh.itss.beadle.application.callback;

import com.bbh.itss.beadle.application.UseCase;
import com.bbh.itss.beadle.application.WithoutTransaction;
import com.bbh.itss.beadle.application.callback.port.in.CallbacksUseCase;
import com.bbh.itss.beadle.application.change.port.in.ProductionChangesUseCase;
import com.bbh.itss.beadle.domain.callback.InternalCallback;
import com.bbh.itss.beadle.domain.callback.ProTechCallback;
import com.bbh.itss.beadle.domain.change.ProductionChange;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@UseCase
@RequiredArgsConstructor
public class CallbackService implements CallbacksUseCase {

    private final ProductionChangesUseCase changes;

    @Override
    @WithoutTransaction
    public Optional<ProductionChange> proTech(ProTechCallback callback) {
        return changes.resync(callback.number());
    }

    @Override
    @WithoutTransaction
    public void internal(InternalCallback callback) {
        // Beadle keeps only the key of the tickets it created; the audit trail records the new status.
    }
}
