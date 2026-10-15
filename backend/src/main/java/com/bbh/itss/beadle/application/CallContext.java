package com.bbh.itss.beadle.application;

import lombok.NoArgsConstructor;

import java.util.Optional;
import java.util.UUID;

import static lombok.AccessLevel.PRIVATE;

/**
 * What a use case runs within, bound to the thread for the time of a call: the correlation id of the user action
 * (bound by the correlation filter for every request, or taken from a callback) and, when no user caused the call,
 * the system it is made for ({@code system:protech} for a callback of Beadle-ProTech). Beadle-Internal and
 * Beadle-ProTech receive both with every call; the audit trail records both.
 */
@NoArgsConstructor(access = PRIVATE)
public final class CallContext {

    /** The user named to Beadle-Internal and Beadle-ProTech for a call no user caused. */
    public static final String SYSTEM = "system";

    /** The actor of what Beadle does by itself when it starts, such as storing the demo catalogue. */
    public static final String STARTUP = "system:startup";

    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SYSTEM_ACTOR = new ThreadLocal<>();

    public static Optional<String> correlationId() {
        return Optional.ofNullable(CORRELATION_ID.get());
    }

    /** The system a call is made for, such as {@code system:protech}; empty while a user is acting. */
    public static Optional<String> systemActor() {
        return Optional.ofNullable(SYSTEM_ACTOR.get());
    }

    /** Binds the correlation id until the scope is closed; a value that is not a UUID leaves the bound one. */
    public static Scope correlatedBy(String correlationId) {
        return isCorrelationId(correlationId) ? bind(CORRELATION_ID, correlationId.trim()) : () -> {
        };
    }

    /** Binds a correlation id unless one is bound already, for a use case called outside a request. */
    public static Scope correlated() {
        return CORRELATION_ID.get() == null ? bind(CORRELATION_ID, UUID.randomUUID().toString()) : () -> {
        };
    }

    /** Lets what runs until the scope is closed act for the given system instead of the signed-in user. */
    public static Scope actingAs(String system) {
        return bind(SYSTEM_ACTOR, system);
    }

    public static boolean isCorrelationId(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            return UUID.fromString(text.trim()).toString().equalsIgnoreCase(text.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Scope bind(ThreadLocal<String> holder, String value) {
        String previous = holder.get();
        holder.set(value);
        return () -> {
            if (previous == null) {
                holder.remove();
            } else {
                holder.set(previous);
            }
        };
    }

    /** Restores what was bound before; meant for try-with-resources. */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
