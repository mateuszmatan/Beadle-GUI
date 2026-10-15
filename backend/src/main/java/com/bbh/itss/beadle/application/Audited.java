package com.bbh.itss.beadle.application;

import com.bbh.itss.beadle.domain.audit.Action;
import com.bbh.itss.beadle.domain.audit.AuditedObject;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Records every call of a use-case method as one row of the audit trail: who, the correlation id, the object (taken
 * from the result or the arguments), the arguments as JSON without secrets, the outcome and the duration. The row
 * joins the use case's read-write transaction; a failure, and every call of a {@link WithoutTransaction} method, is
 * written in a transaction of its own so that it stays when the use case rolls back. A call that fails is preceded by
 * a row for each object it noted it had {@link AuditNotes#storedOnTheWay stored on its way}.
 */
@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface Audited {

    Action value();

    /**
     * Records the call only when the use case calls {@link AuditNotes#tookPlace(AuditedObject)}, for a read that is an
     * action only sometimes (opening or listing changes that pulled a newer state from ProTech), with one row for each
     * object noted. When the call fails, the object noted last, the one it was busy with, gets the {@code FAILURE} row.
     */
    boolean onlyWhenNoted() default false;
}
