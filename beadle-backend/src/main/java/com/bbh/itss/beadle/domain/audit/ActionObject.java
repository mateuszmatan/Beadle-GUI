package com.bbh.itss.beadle.domain.audit;

/**
 * The object an action is about: its type, Beadle's database id when it has one, and the key a person would search
 * for (the CHG or CTASK number, the product code, the department name or the ticket key).
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
}
