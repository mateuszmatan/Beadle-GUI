package com.bbh.itss.beadle.application;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks the parameter of an {@link Audited} use-case method that holds the key of the action's object, such as the name
 * of a business service to create. The row of a call whose result does not name its object, a refused call above all,
 * takes its {@code OBJECT_KEY} from it.
 */
@Documented
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface AuditedKey {
}
