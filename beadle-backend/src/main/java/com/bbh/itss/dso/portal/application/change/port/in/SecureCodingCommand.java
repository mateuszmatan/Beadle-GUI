package com.bbh.itss.dso.portal.application.change.port.in;

import com.bbh.itss.dso.portal.domain.change.SecureCoding;

public record SecureCodingCommand(Long version, Long departmentId, SecureCoding secureCoding,
                                  String implementationDate) {
}
