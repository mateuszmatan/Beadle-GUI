package com.bbh.itss.beadle.adapter.out.cybertrack;

import com.bbh.itss.beadle.application.change.port.out.CyberTrackPort;
import com.bbh.itss.beadle.domain.change.SecureCodingTicket;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static com.bbh.itss.beadle.adapter.out.cybertrack.CyberTrackProperties.NOT_CONFIGURED;

/**
 * The demo CyberTrack Beadle-GUI carried before Beadle-Internal owned the CyberTrack calls; profile
 * {@code stub-services} puts it in front of {@code InternalCyberTrackAdapter} while {@code beadle.cybertrack.url} is
 * blank.
 */
@Primary
@ConditionalOnExpression(NOT_CONFIGURED)
@RequiredArgsConstructor
public class DemoCyberTrackAdapter implements CyberTrackPort {

    private final AtomicInteger tickets = new AtomicInteger(1_000 + new SecureRandom().nextInt(8_000));
    private final CyberTrackProperties properties;

    @Override
    public boolean connected() {
        return false;
    }

    @Override
    public String create(SecureCodingTicket ticket) {
        return properties.projectKey() + "-" + tickets.incrementAndGet();
    }
}
