package com.bbh.itss.beadle.application.change.port.in;

/**
 * Which systems behind Beadle are connected, and whether Beadle-Internal (Jira and CyberTrack) and Beadle-ProTech
 * answered at all: a system that is not connected while its service answers runs as a demo; one whose service did not
 * answer cannot be used until it answers again, which is no demo.
 */
public record ChangeIntegrations(boolean jiraConnected, boolean proTechConnected, boolean cyberTrackConnected,
                                 boolean internalReachable, boolean proTechReachable) {
}
