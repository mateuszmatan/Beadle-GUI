package com.bbh.itss.beadle.application.audit.port.in;

/**
 * Records in the audit trail a request refused before its use case ran, which therefore left no row of its own: a
 * {@code FAILURE} of the action it asked for, by the signed-in user (or the system a callback acts for), under the
 * correlation id of the request.
 */
public interface RefusedRequestsUseCase {

    void refused(RefusedRequest request);
}
