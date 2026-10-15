package com.bbh.itss.beadle.adapter.out.protech;

import com.bbh.itss.beadle.adapter.out.http.BeadleServiceClient;
import com.bbh.itss.beadle.adapter.out.http.CachedAnswer;
import com.bbh.itss.beadle.adapter.out.http.CallerHeaders;
import com.bbh.itss.beadle.application.change.port.out.ProTechPort.RaisedChange;
import com.bbh.itss.beadle.config.ServicesProperties;
import com.bbh.itss.beadle.domain.change.ApprovalRef;
import com.bbh.itss.beadle.domain.change.ChangeTask;
import com.bbh.itss.beadle.domain.change.Lookup;
import com.bbh.itss.beadle.domain.change.ProductionChange;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * The REST API of Beadle-ProTech, {@code /api/protech} (docs/CONTRACTS.md): the changes in ProTech as Beadle raises,
 * reads, updates and reminds them, and the lookups of ProTech. The bodies are the JSON of {@link ProductionChange}
 * and {@link ChangeTask}.
 */
@Component
public class ProTechClient extends BeadleServiceClient {

    public static final String SERVICE = "Beadle-ProTech";

    private static final ParameterizedTypeReference<Map<String, ProductionChange>> CHANGES =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<String>> NAMES = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Lookup>> LOOKUPS = new ParameterizedTypeReference<>() {
    };

    private final CachedAnswer<Optional<Status>> status;

    ProTechClient(ServicesProperties properties, RestClient.Builder builder, CallerHeaders caller, Clock clock) {
        super(SERVICE, properties.protech().url(), properties.token(), builder, caller);
        this.status = new CachedAnswer<>(STATUS_LIFETIME, clock);
    }

    /** What {@code GET /api/protech/status} answered within the last 30 seconds; empty when it did not answer. */
    public Optional<Status> status() {
        return status.get(() -> {
            try {
                return Optional.ofNullable(call("tell its status",
                        client -> client.get().uri("/api/protech/status").retrieve().body(Status.class)));
            } catch (RuntimeException unanswered) {
                return Optional.empty();
            }
        });
    }

    public RaisedChange raise(ProductionChange draft) {
        return call("raise the change", client -> client.post().uri("/api/protech/changes")
                .contentType(APPLICATION_JSON).body(draft).retrieve().body(RaisedChange.class));
    }

    public String createTask(String changeNumber, ChangeTask task) {
        CreatedTask created = call("create a change task on " + changeNumber, client -> client.post()
                .uri("/api/protech/changes/{number}/tasks", changeNumber).contentType(APPLICATION_JSON).body(task)
                .retrieve().body(CreatedTask.class));
        return created == null ? null : created.number();
    }

    public Map<String, ProductionChange> read(Collection<ProductionChange> known) {
        Map<String, ProductionChange> read = call("read the changes", client -> client.post()
                .uri("/api/protech/changes/read").contentType(APPLICATION_JSON).body(List.copyOf(known))
                .retrieve().body(CHANGES));
        return read == null ? Map.of() : read;
    }

    public void update(ProductionChange change) {
        call("update " + change.number(), client -> client.put()
                .uri("/api/protech/changes/{number}", change.number()).contentType(APPLICATION_JSON).body(change)
                .retrieve().toBodilessEntity());
    }

    public List<String> remind(String changeNumber, ApprovalRef approval) {
        List<String> reminded = call("send reminders on " + changeNumber, client -> client.post()
                .uri("/api/protech/changes/{number}/reminders", changeNumber).contentType(APPLICATION_JSON)
                .body(approval).retrieve().body(NAMES));
        return reminded == null ? List.of() : reminded;
    }

    public List<Lookup> lookups(String kind, String query, int limit) {
        List<Lookup> found = call("look up " + kind, client -> client.get()
                .uri(uri -> uri.path("/api/protech/lookups/{kind}")
                        .queryParamIfPresent("q", Optional.ofNullable(query)).queryParam("limit", limit).build(kind))
                .retrieve().body(LOOKUPS));
        return found == null ? List.of() : found;
    }

    /** {@code {"connected": false, "mode": "demo"}} or {@code {"connected": true, "mode": "live"}}. */
    public record Status(boolean connected, String mode) {
    }

    record CreatedTask(String number) {
    }
}
