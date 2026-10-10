package com.bbh.itss.dso.portal.application.catalog.port.in;

import java.time.Instant;

public record ProductView(long id, String code, String name, String ownerTeam, String contactEmail,
                          Long departmentId, String departmentName, long version, Instant updatedAt) {
}
