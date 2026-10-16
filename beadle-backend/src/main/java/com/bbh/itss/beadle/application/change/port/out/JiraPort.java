package com.bbh.itss.beadle.application.change.port.out;

import com.bbh.itss.beadle.domain.change.JiraIssue;
import com.bbh.itss.beadle.domain.change.JiraVersion;

import java.util.Collection;
import java.util.List;

public interface JiraPort {

    boolean connected();

    /**
     * Whether the service behind the port answered when last asked; a system that is not {@link #connected()} but
     * reachable runs as a demo, one that is not reachable cannot be used at all for now.
     */
    default boolean reachable() {
        return true;
    }

    List<JiraVersion> versions(String project);

    List<JiraIssue> epics(String project, String fixVersion);

    List<JiraIssue> stories(String project, String fixVersion, Collection<String> epicKeys);
}
