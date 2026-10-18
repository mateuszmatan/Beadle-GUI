package com.bbh.itss.beadle.application.audit.port.out;

import com.bbh.itss.beadle.domain.audit.ObjectType;

import java.util.Optional;

/** Finds the key a person would search for (change number, product code, business service name) of a stored object. */
public interface ObjectKeyPort {

    Optional<String> keyOf(ObjectType type, long id);
}
