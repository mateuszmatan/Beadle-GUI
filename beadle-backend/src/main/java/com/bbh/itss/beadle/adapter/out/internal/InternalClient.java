package com.bbh.itss.beadle.adapter.out.internal;

import com.bbh.itss.beadle.adapter.out.http.BeadleServiceClient;
import com.bbh.itss.beadle.adapter.out.http.CachedAnswer;
import com.bbh.itss.beadle.adapter.out.http.CallerHeaders;
import com.bbh.itss.beadle.config.ServicesProperties;
import com.bbh.itss.beadle.domain.change.JiraIssue;
import com.bbh.itss.beadle.domain.change.JiraVersion;
import com.bbh.itss.beadle.domain.change.SecureCoding;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * The REST API of Beadle-Internal, {@code /api/internal} (docs/CONTRACTS.md), as far as Beadle-GUI uses it: the
 * versions, epics and stories of the BBH Jira, the secure coding tickets of CyberTrack and the status of both.
 */
@Component
public class InternalClient extends BeadleServiceClient {

    public static final String SERVICE = "Beadle-Internal";

    private static final ParameterizedTypeReference<List<JiraVersion>> VERSIONS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<JiraIssue>> ISSUES = new ParameterizedTypeReference<>() {
    };

    private final CachedAnswer<Optional<Status>> status;

    InternalClient(ServicesProperties properties, RestClient.Builder builder, CallerHeaders caller, Clock clock) {
        super(SERVICE, properties.internal().url(), properties.token(), builder, caller);
        this.status = new CachedAnswer<>(STATUS_LIFETIME, clock);
    }

    /** What {@code GET /api/internal/status} answered within the last 30 seconds; empty when it did not answer. */
    public Optional<Status> status() {
        return status.get(() -> {
            try {
                return Optional.ofNullable(call("tell its status",
                        client -> client.get().uri("/api/internal/status").retrieve().body(Status.class)));
            } catch (RuntimeException unanswered) {
                return Optional.empty();
            }
        });
    }

    public List<JiraVersion> versions(String project) {
        return listOf(call("read the versions of Jira project " + project, client -> client.get()
                .uri("/api/internal/jira/projects/{key}/versions", project).retrieve().body(VERSIONS)));
    }

    public List<JiraIssue> epics(String project, String fixVersion) {
        return listOf(call("read the epics of " + fixVersion + " in Jira project " + project, client -> client.get()
                .uri(uri -> uri.path("/api/internal/jira/projects/{key}/epics").queryParam("fixVersion", "{version}")
                        .build(project, fixVersion))
                .retrieve().body(ISSUES)));
    }

    public List<JiraIssue> stories(String project, String fixVersion, Collection<String> epicKeys) {
        return listOf(call("read the stories of " + fixVersion + " in Jira project " + project, client -> client.get()
                .uri(uri -> uri.path("/api/internal/jira/projects/{key}/stories").queryParam("fixVersion", "{version}")
                        .queryParam("epicKeys", "{epics}").build(project, fixVersion, String.join(",", epicKeys)))
                .retrieve().body(ISSUES)));
    }

    public CreatedTicket createTicket(TicketRequest ticket) {
        return call("create the secure coding ticket of " + ticket.changeNumber(), client -> client.post()
                .uri("/api/internal/cybertrack/tickets").contentType(APPLICATION_JSON).body(ticket).retrieve()
                .body(CreatedTicket.class));
    }

    private static <T> List<T> listOf(List<T> answer) {
        return answer == null ? List.of() : answer;
    }

    /** The status of the systems behind Beadle-Internal, each {@code {"connected": false, "mode": "demo"}}. */
    public record Status(SystemStatus jira, SystemStatus cyberTrack, SystemStatus confluence, SystemStatus qtest) {
    }

    public record SystemStatus(boolean connected, String mode) {
    }

    /** The former {@code SecureCodingTicket}: what CyberTrack needs to scan a release. */
    public record TicketRequest(String changeNumber, String applicationName, LocalDate implementationDate,
                                SecureCoding inputs) {
    }

    public record CreatedTicket(String key, String url) {
    }
}
