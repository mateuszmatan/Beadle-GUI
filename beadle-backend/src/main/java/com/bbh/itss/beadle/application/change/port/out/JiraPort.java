package com.bbh.itss.beadle.application.change.port.out;

import com.bbh.itss.beadle.domain.change.JiraIssue;
import com.bbh.itss.beadle.domain.change.JiraVersion;

import java.util.Collection;
import java.util.List;

public interface JiraPort {

    boolean connected();

    List<JiraVersion> versions(String project);

    List<JiraIssue> epics(String project, String fixVersion);

    List<JiraIssue> stories(String project, String fixVersion, Collection<String> epicKeys);
}
