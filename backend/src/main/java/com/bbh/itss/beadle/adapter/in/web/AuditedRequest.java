package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.domain.audit.Action;
import com.bbh.itss.beadle.domain.audit.ObjectType;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Names the action a request to this endpoint asks for, so that a request the API refuses before the action's
 * {@code @Audited} use case runs (a body it cannot read, a field that fails validation, a path value of the wrong type)
 * still leaves its {@code FAILURE} row in the audit trail ({@link RefusedRequests}). Every endpoint that changes
 * something carries it, with the action its use case records.
 */
@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface AuditedRequest {

    Action value();

    /**
     * The property of the body with Beadle's id of the object of the action, when the path has no {@code {id}}, such
     * as the product a change is drafted for.
     */
    String objectId() default "";

    /**
     * The property of the body with the key of the object of the action, when the path has no {@code {id}}: the code
     * of a product, the name of a department, the number of a change or the key of a ticket.
     */
    String objectKey() default "";

    /** The type of the object the body names when it is not the type of the action: a raise is filed by product. */
    ObjectType[] objectType() default {};

    /** The system the endpoint acts for, such as {@code system:protech} for a callback; the signed-in user if empty. */
    String actor() default "";
}
