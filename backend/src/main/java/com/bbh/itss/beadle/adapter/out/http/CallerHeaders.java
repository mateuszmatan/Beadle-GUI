package com.bbh.itss.beadle.adapter.out.http;

import com.bbh.itss.beadle.application.CallContext;
import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort;
import com.bbh.itss.beadle.config.ServiceHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

import static com.bbh.itss.beadle.application.CallContext.SYSTEM;

/**
 * Names on every call to Beadle-Internal and Beadle-ProTech who caused it, {@code X-Beadle-User}: the signed-in user,
 * or {@code system} while Beadle acts for a system (a callback); and the user action it belongs to,
 * {@code X-Correlation-Id}.
 */
@Component
@RequiredArgsConstructor
public class CallerHeaders implements ClientHttpRequestInterceptor {

    private final SignedInUserPort signedIn;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().set(ServiceHeaders.USER, user());
        request.getHeaders().set(ServiceHeaders.CORRELATION_ID,
                CallContext.correlationId().orElseGet(() -> UUID.randomUUID().toString()));
        return execution.execute(request, body);
    }

    String user() {
        return CallContext.systemActor().isPresent() ? SYSTEM : signedIn.name();
    }
}
