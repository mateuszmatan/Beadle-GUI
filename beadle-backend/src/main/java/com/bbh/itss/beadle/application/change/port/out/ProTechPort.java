package com.bbh.itss.beadle.application.change.port.out;

import com.bbh.itss.beadle.domain.change.ApprovalRef;
import com.bbh.itss.beadle.domain.change.ChangeTask;
import com.bbh.itss.beadle.domain.change.ProductionChange;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ProTechPort {

    boolean proTechConnected();

    /** Whether the service behind the port answered when last asked, as {@link JiraPort#reachable()}. */
    boolean reachable();

    RaisedChange raise(ProductionChange change);

    String createTask(String changeNumber, ChangeTask task);

    Map<String, ProductionChange> read(Collection<ProductionChange> known);

    void update(ProductionChange change);

    List<String> remind(String changeNumber, ApprovalRef approval);

    record RaisedChange(String number, String url) {
    }
}
