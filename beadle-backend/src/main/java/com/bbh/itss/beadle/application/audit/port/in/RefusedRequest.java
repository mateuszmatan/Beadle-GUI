package com.bbh.itss.beadle.application.audit.port.in;

import com.bbh.itss.beadle.domain.audit.Action;
import com.bbh.itss.beadle.domain.audit.ActionObject;

/**
 * A request for an action that the API refused before the action's use case ran: a body it could not read, a field
 * that failed validation, a path or query value of the wrong type.
 *
 * @param action  the action the request asked for
 * @param object  what the request named as the object of the action, as far as it could be read; may be
 *                {@code null}, and a known id without a key gets its key from the stored object
 * @param details the request as JSON, secrets masked, like the arguments of an action that ran
 * @param message why it was refused: the field problems, or what could not be read
 */
public record RefusedRequest(Action action, ActionObject object, String details, String message) {
}
