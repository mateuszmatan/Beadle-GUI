package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.change.port.in.SecureCodingCommand;
import com.bbh.itss.dso.portal.domain.change.SecureCoding;
import jakarta.validation.constraints.NotNull;

public record SecureCodingRequest(
        @NotNull Long version,
        @NotNull Long departmentId,
        String apoNumber,
        String implementationDate,
        String bitbucketUrl,
        String artifactLink,
        String qcApplicationLink) {

    SecureCodingCommand toCommand() {
        return new SecureCodingCommand(version, departmentId,
                new SecureCoding(apoNumber, bitbucketUrl, artifactLink, qcApplicationLink), implementationDate);
    }
}
