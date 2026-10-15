package com.bbh.itss.beadle.application;

import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

/**
 * Lets a use case audited {@link Audited#onlyWhenNoted() only when noted} say that its action took place. Outside
 * such a use case a note has no effect.
 */
@NoArgsConstructor(access = PRIVATE)
public final class AuditNotes {

    private static final ThreadLocal<Listening> LISTENING = new ThreadLocal<>();

    /** Tells the audit trail that the conditional action of the running use case took place. */
    public static void tookPlace() {
        Listening listening = LISTENING.get();
        if (listening != null) {
            listening.tookPlace = true;
        }
    }

    /** Listens for {@link #tookPlace()} until the returned scope is closed; used by the audit interceptor. */
    public static Listening listen() {
        Listening listening = new Listening(LISTENING.get());
        LISTENING.set(listening);
        return listening;
    }

    /** The notes of one audited call; closing it restores the listening of an enclosing call. */
    public static final class Listening implements CallContext.Scope {

        private final Listening enclosing;
        private boolean tookPlace;

        private Listening(Listening enclosing) {
            this.enclosing = enclosing;
        }

        public boolean tookPlace() {
            return tookPlace;
        }

        @Override
        public void close() {
            if (enclosing == null) {
                LISTENING.remove();
            } else {
                LISTENING.set(enclosing);
            }
        }
    }
}
