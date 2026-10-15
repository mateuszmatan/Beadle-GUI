package com.bbh.itss.beadle.application.change.port.out;

import com.bbh.itss.beadle.domain.change.SecureCodingTicket;

public interface CyberTrackPort {

    boolean connected();

    String create(SecureCodingTicket ticket);
}
