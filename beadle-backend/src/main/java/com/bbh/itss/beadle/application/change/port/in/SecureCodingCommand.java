package com.bbh.itss.beadle.application.change.port.in;

import com.bbh.itss.beadle.domain.change.SecureCoding;

public record SecureCodingCommand(Long version, Long businessServiceId, SecureCoding secureCoding,
                                  String implementationDate) {
}
