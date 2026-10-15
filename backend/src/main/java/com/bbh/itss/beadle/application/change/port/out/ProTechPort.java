package com.bbh.itss.beadle.application.change.port.out;

import com.bbh.itss.beadle.domain.change.ApprovalRef;
import com.bbh.itss.beadle.domain.change.ChangeTask;
import com.bbh.itss.beadle.domain.change.ProductionChange;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ProTechPort {

    boolean connected();

    /**
     * Whether the service behind the port answered when last asked; a system that is not {@link #connected()} but
     * reachable runs as a demo, one that is not reachable cannot be used at all for now.
     */
    default boolean reachable() {
        return true;
    }

    RaisedChange raise(ProductionChange change);

    String createTask(String changeNumber, ChangeTask task);

    Map<String, ProductionChange> read(Collection<ProductionChange> known);

    void update(ProductionChange change);

    List<String> remind(String changeNumber, ApprovalRef approval);

    record RaisedChange(String number, String url) {
    }
}
