package com.bbh.itss.beadle.adapter.out.protech;

import com.bbh.itss.beadle.application.change.port.out.ProTechLookupPort;
import com.bbh.itss.beadle.application.change.port.out.ProTechPort;
import com.bbh.itss.beadle.domain.change.ApprovalRef;
import com.bbh.itss.beadle.domain.change.ChangeTask;
import com.bbh.itss.beadle.domain.change.Lookup;
import com.bbh.itss.beadle.domain.change.LookupKind;
import com.bbh.itss.beadle.domain.change.ProductionChange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * ProTech as Beadle-ProTech serves it: the changes, their tasks and reminders and the lookups go to
 * {@code /api/protech}, and ProTech counts as connected while Beadle-ProTech answers its status as live.
 */
@Component
@RequiredArgsConstructor
class RestProTechAdapter implements ProTechPort, ProTechLookupPort {

    static final String NO_NUMBER = " answered without the number of the new ";

    private final ProTechClient client;

    @Override
    public boolean connected() {
        return client.status().map(ProTechClient.Status::connected).orElse(false);
    }

    @Override
    public boolean reachable() {
        return client.status().isPresent();
    }

    @Override
    public RaisedChange raise(ProductionChange change) {
        RaisedChange raised = client.raise(change);
        if (raised == null || isBlank(raised.number())) {
            throw unanswered("change");
        }
        return raised;
    }

    @Override
    public String createTask(String changeNumber, ChangeTask task) {
        String number = client.createTask(changeNumber, task);
        if (isBlank(number)) {
            throw unanswered("change task");
        }
        return number;
    }

    @Override
    public Map<String, ProductionChange> read(Collection<ProductionChange> known) {
        return known.isEmpty() ? Map.of() : client.read(known);
    }

    @Override
    public void update(ProductionChange change) {
        client.update(change);
    }

    @Override
    public List<String> remind(String changeNumber, ApprovalRef approval) {
        return client.remind(changeNumber, approval);
    }

    @Override
    public List<Lookup> find(LookupKind kind, String query, int limit) {
        return client.lookups(kind.path(), query, limit);
    }

    private UncheckedIOException unanswered(String what) {
        String message = client.service() + NO_NUMBER + what;
        return new UncheckedIOException(message, new IOException(message));
    }
}
