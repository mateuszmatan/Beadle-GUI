package com.bbh.itss.beadle.domain.change;

public record Lookup(String value, String detail) {

    public static final int MAX_LOOKUPS = 20;
}
