package com.bbh.itss.beadle.adapter.out.internal;

import com.bbh.itss.beadle.application.change.port.out.JiraPort;
import com.bbh.itss.beadle.domain.change.JiraIssue;
import com.bbh.itss.beadle.domain.change.JiraVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * The BBH Jira of the delivery teams as Beadle-Internal serves it; Jira counts as connected while Beadle-Internal
 * answers its status of Jira as connected.
 */
@Component
@RequiredArgsConstructor
class InternalJiraAdapter implements JiraPort {

    private final InternalClient client;

    @Override
    public boolean connected() {
        return client.status().map(InternalClient.Status::jira).map(InternalClient.SystemStatus::connected)
                .orElse(false);
    }

    @Override
    public List<JiraVersion> versions(String project) {
        return client.versions(project);
    }

    @Override
    public List<JiraIssue> epics(String project, String fixVersion) {
        return client.epics(project, fixVersion);
    }

    @Override
    public List<JiraIssue> stories(String project, String fixVersion, Collection<String> epicKeys) {
        return epicKeys.isEmpty() ? List.of() : client.stories(project, fixVersion, epicKeys);
    }
}
