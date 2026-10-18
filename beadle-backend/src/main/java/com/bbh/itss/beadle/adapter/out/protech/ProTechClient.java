package com.bbh.itss.beadle.adapter.out.protech;

import com.bbh.itss.beadle.adapter.out.http.BeadleServiceClient;
import com.bbh.itss.beadle.adapter.out.http.CallerHeaders;
import com.bbh.itss.beadle.adapter.out.http.ServicesProperties;
import com.bbh.itss.beadle.application.change.port.out.ProTechLookupPort;
import com.bbh.itss.beadle.application.change.port.out.ProTechPort;
import com.bbh.itss.beadle.domain.change.ApprovalRef;
import com.bbh.itss.beadle.domain.change.ChangeTask;
import com.bbh.itss.beadle.domain.change.Lookup;
import com.bbh.itss.beadle.domain.change.LookupKind;
import com.bbh.itss.beadle.domain.change.ProductionChange;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.apache.commons.collections4.MapUtils.emptyIfNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Beadle-ProTech, {@code /api/protech} (docs/CONTRACTS.md): the changes in ProTech as Beadle raises, reads, updates
 * and reminds them, and the lookups of ProTech; ProTech counts as connected while Beadle-ProTech answers its status as
 * live. The bodies are the JSON of {@link ProductionChange} and {@link ChangeTask}.
 */
@Component
public class ProTechClient extends BeadleServiceClient<ProTechClient.Status> implements ProTechPort,
        ProTechLookupPort {

    public static final String SERVICE = "Beadle-ProTech";

    private static final ParameterizedTypeReference<Map<String, ProductionChange>> CHANGES =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<String>> NAMES = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Lookup>> LOOKUPS = new ParameterizedTypeReference<>() {
    };
    private static final String LOOKUPS_PATH = "/api/protech/lookups/{kind}";

    ProTechClient(ServicesProperties properties, RestClient.Builder builder, CallerHeaders caller, Clock clock) {
        super(SERVICE, properties.protech().url(), properties.token(), "/api/protech/status", Status.class, builder,
                caller, clock);
    }

    @Override
    public boolean proTechConnected() {
        return status().map(Status::connected).orElse(false);
    }

    @Override
    public RaisedChange raise(ProductionChange draft) {
        return required(call("raise the change", client -> client.post().uri("/api/protech/changes")
                        .contentType(APPLICATION_JSON).body(draft).retrieve().body(RaisedChange.class)),
                RaisedChange::number, "number of the new change");
    }

    @Override
    public String createTask(String changeNumber, ChangeTask task) {
        return required(call("create a change task on " + changeNumber, client -> client.post()
                        .uri("/api/protech/changes/{number}/tasks", changeNumber).contentType(APPLICATION_JSON)
                        .body(task).retrieve().body(CreatedTask.class)),
                CreatedTask::number, "number of the new change task").number();
    }

    @Override
    public Map<String, ProductionChange> read(Collection<ProductionChange> known) {
        return known.isEmpty() ? Map.of() : emptyIfNull(call("read the changes", client -> client.post()
                .uri("/api/protech/changes/read").contentType(APPLICATION_JSON).body(List.copyOf(known))
                .retrieve().body(CHANGES)));
    }

    @Override
    public void update(ProductionChange change) {
        call("update " + change.number(), client -> client.put()
                .uri("/api/protech/changes/{number}", change.number()).contentType(APPLICATION_JSON).body(change)
                .retrieve().toBodilessEntity());
    }

    @Override
    public List<String> remind(String changeNumber, ApprovalRef approval) {
        return list("send reminders on " + changeNumber, client -> client.post()
                .uri("/api/protech/changes/{number}/reminders", changeNumber).contentType(APPLICATION_JSON)
                .body(approval).retrieve().body(NAMES));
    }

    /**
     * The text the user typed travels as a URI variable, never as part of the template, so that braces, {@code &}
     * and {@code +} reach Beadle-ProTech encoded and as typed.
     */
    @Override
    public List<Lookup> find(LookupKind kind, String query, int limit) {
        return list("look up " + kind.path(), client -> client.get()
                .uri(uri -> query == null
                        ? uri.path(LOOKUPS_PATH).queryParam("limit", "{limit}").build(kind.path(), limit)
                        : uri.path(LOOKUPS_PATH).queryParam("q", "{q}").queryParam("limit", "{limit}")
                        .build(kind.path(), query, limit))
                .retrieve().body(LOOKUPS));
    }

    /** {@code {"connected": false, "mode": "demo"}} or {@code {"connected": true, "mode": "live"}}. */
    record Status(boolean connected) {
    }

    record CreatedTask(String number) {
    }
}
