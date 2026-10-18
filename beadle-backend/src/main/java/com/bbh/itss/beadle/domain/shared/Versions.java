package com.bbh.itss.beadle.domain.shared;

import lombok.NoArgsConstructor;

import static com.bbh.itss.beadle.domain.shared.Failures.staleVersion;
import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class Versions {

    public static void requireReadAt(Long expected, long current) {
        if (expected == null || expected != current) {
            throw staleVersion();
        }
    }

    public static void requireUnchangedSince(Long expected, long changed, long current) {
        if (expected != null && (expected < changed || expected > current)) {
            throw staleVersion();
        }
    }
}
