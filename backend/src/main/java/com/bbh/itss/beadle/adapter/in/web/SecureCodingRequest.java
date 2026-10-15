package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.application.change.port.in.SecureCodingCommand;
import com.bbh.itss.beadle.domain.change.SecureCoding;
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
