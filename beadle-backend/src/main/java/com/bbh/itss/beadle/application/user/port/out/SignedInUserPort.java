package com.bbh.itss.beadle.application.user.port.out;

import java.util.Optional;

/** Who is signed in to Beadle for the current call. */
public interface SignedInUserPort {

    /** The name given when nobody is signed in for the call, such as for a task Beadle starts by itself. */
    String UNKNOWN = "unknown";

    /** The name of the signed-in user, or {@link #UNKNOWN} when nobody is signed in. */
    String name();

    /** The e-mail address of the signed-in user, when the identity provider names one. */
    default Optional<String> email() {
        return Optional.empty();
    }
}
