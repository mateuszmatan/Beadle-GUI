package com.bbh.itss.dso.portal.adapter.out.persistence;

import jakarta.persistence.Embeddable;

@Embeddable
public record TaskDetailsEmbeddable(String assignmentGroup, String assignedTo, String configurationItem,
                                    String platform, String application, String packages, String backoutPackages,
                                    String importance, String shortDescription, String description,
                                    String additionalComments) {
}
