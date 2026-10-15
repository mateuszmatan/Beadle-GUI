package com.bbh.itss.beadle.application;

import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.AuditedObject;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static lombok.AccessLevel.PRIVATE;

/**
 * What a running {@link Audited} use case tells the audit trail besides its arguments and its result: what it created
 * in another system ({@link #created(String)}: the CHG and CTASK numbers ProTech handed out, the key of a CyberTrack
 * ticket), what else came of it ({@link #result(String, Object)}: the people reminded), and, for a use case audited
 * {@link Audited#onlyWhenNoted() only when noted}, on which objects its action took place
 * ({@link #tookPlace(AuditedObject)}). The notes go to
 * the row of the innermost audited call running on the thread; outside one a note has no effect. A use case notes as
 * it goes, so that a call that fails half-way still records what it had done in the other systems.
 */
@NoArgsConstructor(access = PRIVATE)
public final class AuditNotes {

    private static final ThreadLocal<Listening> LISTENING = new ThreadLocal<>();

    /**
     * Tells the audit trail that the conditional action of the running use case takes place on {@code about}, such as
     * a change whose newer state from ProTech is about to be stored; each object noted gets a row of its own.
     */
    public static void tookPlace(AuditedObject about) {
        Listening listening = LISTENING.get();
        if (listening != null && about != null) {
            listening.noted.add(about.auditedObject());
        }
    }

    /**
     * Records the key of something the action created in another system (a CHG or CTASK number, a ticket key). The
     * keys stand last in the row's details, as {@code "result": {..., "created": [...]}}, where the object-key filter
     * of the audit trail finds them too.
     */
    public static void created(String key) {
        Listening listening = LISTENING.get();
        if (listening != null && key != null && !key.isBlank()) {
            listening.created.add(key.trim());
        }
    }

    /** Records a further outcome of the action under {@code name} in the result section of the row's details. */
    public static void result(String name, Object value) {
        Listening listening = LISTENING.get();
        if (listening != null) {
            listening.results.put(name, value);
        }
    }

    /** Listens for notes until the returned scope is closed; used by the audit interceptor. */
    public static Listening listen() {
        Listening listening = new Listening(LISTENING.get());
        LISTENING.set(listening);
        return listening;
    }

    /** The notes of one audited call; closing it restores the listening of an enclosing call. */
    public static final class Listening implements CallContext.Scope {

        private final Listening enclosing;
        private final Set<String> created = new LinkedHashSet<>();
        private final Map<String, Object> results = new LinkedHashMap<>();
        private final List<ActionObject> noted = new ArrayList<>();

        private Listening(Listening enclosing) {
            this.enclosing = enclosing;
        }

        public boolean tookPlace() {
            return !noted.isEmpty();
        }

        /** The objects the conditional action took place on, in the order noted, each once. */
        public List<ActionObject> noted() {
            return noted.stream().distinct().toList();
        }

        /** The keys of what the action created in other systems, in the order noted, each once. */
        public List<String> created() {
            return List.copyOf(created);
        }

        /** The further outcomes of the action by name, in the order noted. */
        public Map<String, Object> results() {
            return new LinkedHashMap<>(results);
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
