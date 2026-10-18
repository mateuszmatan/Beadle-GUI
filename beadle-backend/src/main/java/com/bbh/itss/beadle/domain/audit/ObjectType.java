package com.bbh.itss.beadle.domain.audit;

/** The kind of object a row of the audit trail is about ({@code OBJECT_TYPE}). */
public enum ObjectType {

    /** A production change; its key is the ProTech number such as {@code CHG0001234}. */
    CHANGE,
    /** A product of the catalogue; its key is the product code. */
    PRODUCT,
    /** The ProTech change template of a product; its id is the product's, its key the product code. */
    CHANGE_PROFILE,
    /** A business service; its key is the business service's name. */
    BUSINESS_SERVICE,
    /** A ticket in a system of Beadle-Internal, such as the CyberTrack secure coding ticket {@code SCP-123}. */
    TICKET,
    /** A person signing in; the key is the user name. */
    USER
}
