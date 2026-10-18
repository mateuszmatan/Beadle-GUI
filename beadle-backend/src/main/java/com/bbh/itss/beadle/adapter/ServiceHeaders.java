package com.bbh.itss.beadle.adapter;

import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

/** The headers every call between the Beadle services carries besides the service token (docs/CONTRACTS.md). */
@NoArgsConstructor(access = PRIVATE)
public final class ServiceHeaders {

    /** The user signed in to Beadle-GUI who caused the call, or {@code system}. */
    public static final String USER = "X-Beadle-User";

    /** The UUID of the user action the call belongs to. */
    public static final String CORRELATION_ID = "X-Correlation-Id";
}
