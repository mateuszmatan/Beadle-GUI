package com.bbh.itss.beadle.adapter.out.internal;

import com.bbh.itss.beadle.adapter.out.internal.InternalClient.CreatedTicket;
import com.bbh.itss.beadle.adapter.out.internal.InternalClient.TicketRequest;
import com.bbh.itss.beadle.application.change.port.out.CyberTrackPort;
import com.bbh.itss.beadle.domain.change.SecureCodingTicket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static java.time.format.DateTimeFormatter.ofPattern;
import static java.time.format.ResolverStyle.STRICT;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * CyberTrack, the Jira of the Cybersecurity team, as Beadle-Internal serves it: a secure coding ticket is created
 * through {@code POST /api/internal/cybertrack/tickets}, its implementation date turned from the {@code MMDDYYYY} the
 * form asks for into an ISO date.
 */
@Component
@RequiredArgsConstructor
class InternalCyberTrackAdapter implements CyberTrackPort {

    static final String NO_KEY = " answered without the key of the new secure coding ticket";
    private static final DateTimeFormatter FORM_DATE = ofPattern("MMdduuuu").withResolverStyle(STRICT);

    private final InternalClient client;

    @Override
    public boolean connected() {
        return client.status().map(InternalClient.Status::cyberTrack).map(InternalClient.SystemStatus::connected)
                .orElse(false);
    }

    @Override
    public String create(SecureCodingTicket ticket) {
        CreatedTicket created = client.createTicket(new TicketRequest(ticket.changeNumber(), ticket.applicationName(),
                LocalDate.parse(ticket.implementationDate(), FORM_DATE), ticket.inputs()));
        if (created == null || isBlank(created.key())) {
            String message = client.service() + NO_KEY;
            throw new UncheckedIOException(message, new IOException(message));
        }
        return created.key();
    }
}
