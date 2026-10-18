package com.bbh.itss.beadle.adapter.out.internal;

import com.bbh.itss.beadle.adapter.out.http.BeadleServiceClient;
import com.bbh.itss.beadle.adapter.out.http.CallerHeaders;
import com.bbh.itss.beadle.application.change.port.out.CyberTrackPort;
import com.bbh.itss.beadle.application.change.port.out.JiraPort;
import com.bbh.itss.beadle.config.ServicesProperties;
import com.bbh.itss.beadle.domain.change.JiraIssue;
import com.bbh.itss.beadle.domain.change.JiraVersion;
import com.bbh.itss.beadle.domain.change.SecureCodingTicket;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Collection;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Beadle-Internal, {@code /api/internal} (docs/CONTRACTS.md), as far as Beadle-GUI uses it: the versions, epics and
 * stories of the BBH Jira and the secure coding tickets of CyberTrack, each connected as Beadle-Internal reports it.
 */
@Component
public class InternalClient extends BeadleServiceClient<InternalClient.Status> implements JiraPort, CyberTrackPort {

    public static final String SERVICE = "Beadle-Internal";

    private static final ParameterizedTypeReference<List<JiraVersion>> VERSIONS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<JiraIssue>> ISSUES = new ParameterizedTypeReference<>() {
    };

    InternalClient(ServicesProperties properties, RestClient.Builder builder, CallerHeaders caller, Clock clock) {
        super(SERVICE, properties.internal().url(), properties.token(), "/api/internal/status", Status.class, builder,
                caller, clock);
    }

    @Override
    public boolean jiraConnected() {
        return status().map(Status::jira).map(SystemStatus::connected).orElse(false);
    }

    @Override
    public boolean cyberTrackConnected() {
        return status().map(Status::cyberTrack).map(SystemStatus::connected).orElse(false);
    }

    @Override
    public List<JiraVersion> versions(String project) {
        return list("read the versions of Jira project " + project, client -> client.get()
                .uri("/api/internal/jira/projects/{key}/versions", project).retrieve().body(VERSIONS));
    }

    @Override
    public List<JiraIssue> epics(String project, String fixVersion) {
        return list("read the epics of " + fixVersion + " in Jira project " + project, client -> client.get()
                .uri(uri -> uri.path("/api/internal/jira/projects/{key}/epics").queryParam("fixVersion", "{version}")
                        .build(project, fixVersion))
                .retrieve().body(ISSUES));
    }

    @Override
    public List<JiraIssue> stories(String project, String fixVersion, Collection<String> epicKeys) {
        return epicKeys.isEmpty() ? List.of() : list("read the stories of " + fixVersion + " in Jira project "
                + project, client -> client.get()
                .uri(uri -> uri.path("/api/internal/jira/projects/{key}/stories").queryParam("fixVersion", "{version}")
                        .queryParam("epicKeys", "{epics}").build(project, fixVersion, String.join(",", epicKeys)))
                .retrieve().body(ISSUES));
    }

    /** The ticket travels as it is, its implementation date as an ISO date. */
    @Override
    public String createTicket(SecureCodingTicket ticket) {
        CreatedTicket created = call("create the secure coding ticket of " + ticket.changeNumber(), client -> client
                .post().uri("/api/internal/cybertrack/tickets").contentType(APPLICATION_JSON).body(ticket).retrieve()
                .body(CreatedTicket.class));
        return required(created, CreatedTicket::key, "key of the new secure coding ticket").key();
    }

    /** The status of the systems behind Beadle-Internal, each {@code {"connected": false, "mode": "demo"}}. */
    record Status(SystemStatus jira, SystemStatus cyberTrack) {
    }

    record SystemStatus(boolean connected) {
    }

    record CreatedTicket(String key) {
    }
}
