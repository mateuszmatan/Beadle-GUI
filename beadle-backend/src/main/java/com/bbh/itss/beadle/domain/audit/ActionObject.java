package com.bbh.itss.beadle.domain.audit;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * The object an action is about: its type, Beadle's database id when it has one, and the key a person would search
 * for (the CHG or CTASK number, the product code, the business service name or the ticket key).
 */
public record ActionObject(ObjectType type, Long id, String key) {

    public static ActionObject of(ObjectType type, Long id) {
        return new ActionObject(type, id, null);
    }

    public static ActionObject keyed(ObjectType type, String key) {
        return new ActionObject(type, null, key);
    }

    public ActionObject withKey(String key) {
        return new ActionObject(type, id, key);
    }

    public boolean lacksKey() {
        return key == null && id != null && type != null;
    }

    /** This object with the key {@code keys} finds for its type and id when only its id is known, else as it is. */
    public ActionObject keyedBy(BiFunction<ObjectType, Long, Optional<String>> keys) {
        return lacksKey() ? withKey(keys.apply(type, id).orElse(null)) : this;
    }
}
