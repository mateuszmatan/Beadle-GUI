package com.bbh.itss.beadle.domain.callback;

import java.time.Instant;

/**
 * Beadle-Internal telling Beadle-GUI that an item Beadle created in one of its systems changed status, such as the
 * CyberTrack secure coding ticket {@code SCP-123} moving to Done.
 */
public record InternalCallback(InternalSystem system, String key, String status, Instant at, String correlationId) {
}
