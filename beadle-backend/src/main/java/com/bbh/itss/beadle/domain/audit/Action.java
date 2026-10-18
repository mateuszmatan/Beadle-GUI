package com.bbh.itss.beadle.domain.audit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static com.bbh.itss.beadle.domain.audit.ObjectType.BUSINESS_SERVICE;
import static com.bbh.itss.beadle.domain.audit.ObjectType.CHANGE;
import static com.bbh.itss.beadle.domain.audit.ObjectType.CHANGE_PROFILE;
import static com.bbh.itss.beadle.domain.audit.ObjectType.PRODUCT;
import static com.bbh.itss.beadle.domain.audit.ObjectType.TICKET;
import static com.bbh.itss.beadle.domain.audit.ObjectType.USER;

/**
 * What a person, or Beadle-ProTech and Beadle-Internal through their callbacks, did in Beadle: the {@code ACTION} of
 * a row of the audit trail. Reads are not actions, except {@link #CHANGE_SYNCED} when opening a change pulled a newer
 * state from ProTech.
 */
@Getter
@RequiredArgsConstructor
public enum Action {

    CHANGE_PREVIEWED(PRODUCT, false),
    CHANGE_RAISED(CHANGE, false),
    CHANGE_EDITED(CHANGE, false),
    CHANGE_TASKS_CREATED(CHANGE, false),
    SECURE_CODING_TICKET_CREATED(CHANGE, false),
    REMINDER_SENT(CHANGE, false),
    CHANGE_SYNCED(CHANGE, false),
    PRODUCT_CREATED(PRODUCT, false),
    PRODUCT_UPDATED(PRODUCT, false),
    PRODUCT_DELETED(PRODUCT, true),
    CHANGE_PROFILE_UPDATED(CHANGE_PROFILE, false),
    BUSINESS_SERVICE_CREATED(BUSINESS_SERVICE, false),
    BUSINESS_SERVICE_RENAMED(BUSINESS_SERVICE, false),
    BUSINESS_SERVICE_DELETED(BUSINESS_SERVICE, true),
    USER_SIGNED_IN(USER, false),
    USER_SIGNED_OUT(USER, false),
    CALLBACK_PROTECH(CHANGE, false),
    CALLBACK_INTERNAL(TICKET, false);

    /** The kind of object the action is about when neither its result nor its arguments say more. */
    private final ObjectType objectType;

    /** Whether the action removes its object, so that the key must be read before the action runs. */
    private final boolean deletes;
}
