package com.bbh.itss.beadle.application.change.port.out;

import com.bbh.itss.beadle.domain.change.SecureCodingTicket;

public interface CyberTrackPort {

    boolean connected();

    /**
     * Whether the service behind the port answered when last asked; a system that is not {@link #connected()} but
     * reachable runs as a demo, one that is not reachable cannot be used at all for now.
     */
    default boolean reachable() {
        return true;
    }

    String create(SecureCodingTicket ticket);
}
