package com.bbh.itss.beadle.application.change.port.out;

import com.bbh.itss.beadle.domain.change.SecureCodingTicket;

public interface CyberTrackPort {

    boolean cyberTrackConnected();

    /** Whether the service behind the port answered when last asked, as {@link JiraPort#reachable()}. */
    boolean reachable();

    /** Creates the ticket and answers its key. */
    String createTicket(SecureCodingTicket ticket);
}
