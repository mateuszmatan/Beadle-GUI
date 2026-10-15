package com.bbh.itss.beadle.domain.audit;

/**
 * A result or an argument of a use case that knows which object of the audit trail it stands for, such as a change
 * (its number) or a product (its code).
 */
public interface AuditedObject {

    ActionObject auditedObject();
}
