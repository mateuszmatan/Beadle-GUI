package com.bbh.itss.beadle.application.callback.port.in;

import com.bbh.itss.beadle.domain.callback.InternalCallback;
import com.bbh.itss.beadle.domain.callback.ProTechCallback;
import com.bbh.itss.beadle.domain.change.ProductionChange;

import java.util.Optional;

/** The callbacks of Beadle-ProTech and Beadle-Internal. */
public interface CallbacksUseCase {

    /** Reads the change again from ProTech; empty when Beadle did not create the change. */
    Optional<ProductionChange> proTech(ProTechCallback callback);

    /** Takes note of the new status of an item Beadle-Internal created. */
    void internal(InternalCallback callback);
}
