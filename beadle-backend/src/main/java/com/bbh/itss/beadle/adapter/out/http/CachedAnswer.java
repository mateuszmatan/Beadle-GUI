package com.bbh.itss.beadle.adapter.out.http;

import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/** An answer kept for a while, such as the status of a service, so that not every page asks for it again. */
@RequiredArgsConstructor
public final class CachedAnswer<T> {

    private final Duration lifetime;
    private final Clock clock;
    private Kept<T> kept;

    public synchronized T get(Supplier<T> answer) {
        Instant now = clock.instant();
        if (kept == null || !now.isBefore(kept.until())) {
            kept = new Kept<>(answer.get(), now.plus(lifetime));
        }
        return kept.value();
    }

    private record Kept<T>(T value, Instant until) {
    }
}
