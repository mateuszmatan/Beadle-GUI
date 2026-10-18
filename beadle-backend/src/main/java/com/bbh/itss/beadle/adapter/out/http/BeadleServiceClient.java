package com.bbh.itss.beadle.adapter.out.http;

import com.bbh.itss.beadle.adapter.Secrets;
import com.bbh.itss.beadle.domain.shared.InvalidRequestException;
import com.bbh.itss.beadle.domain.shared.InvalidRequestException.FieldProblem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;

import static java.time.Duration.ofSeconds;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.apache.commons.lang3.Strings.CS;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * A client of Beadle-Internal or Beadle-ProTech that calls the way docs/CONTRACTS.md has it: the service token in
 * {@code Authorization}, the caller in {@code X-Beadle-User} and {@code X-Correlation-Id} ({@link CallerHeaders}),
 * within the timeouts of {@code spring.http.clients}. A service that cannot be reached or answers {@code 5xx} fails the call with
 * an {@link UncheckedIOException} naming the service, which the API answers {@code 503}, and so does a call the HTTP
 * layer could not even send (an {@link IllegalArgumentException} for a header value it refuses, for example); a
 * refusal ({@code 4xx}) keeps its meaning: the field problems of a {@code 400}, not found for {@code 404}, a conflict
 * otherwise. The token is sent trimmed, as the three services compare it, and never appears in a message. The status
 * of the service, {@code S}, is asked at most every 30 seconds.
 */
@Slf4j
public abstract class BeadleServiceClient<S> {

    private static final Duration STATUS_LIFETIME = ofSeconds(30);
    private static final int MAX_REASON = 300;

    private final String service;
    private final String token;
    private final RestClient client;
    private final String statusPath;
    private final Class<S> statusType;
    private final Clock clock;
    private Optional<S> status = Optional.empty();
    private Instant statusUntil = Instant.MIN;

    protected BeadleServiceClient(String service, String url, String token, String statusPath, Class<S> statusType,
                                  RestClient.Builder builder, CallerHeaders caller, Clock clock) {
        this.service = service;
        this.token = trimToEmpty(token);
        this.client = builder.baseUrl(CS.removeEnd(trimToEmpty(url), "/"))
                .defaultHeader(AUTHORIZATION, "Bearer " + this.token)
                .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE)
                .requestInterceptor(caller).build();
        this.statusPath = statusPath;
        this.statusType = statusType;
        this.clock = clock;
    }

    /** Whether the service answered its status within the last 30 seconds. */
    public boolean reachable() {
        return status().isPresent();
    }

    /** What the status endpoint answered within the last 30 seconds; empty when it did not answer. */
    protected synchronized Optional<S> status() {
        Instant now = clock.instant();
        if (!now.isBefore(statusUntil)) {
            try {
                status = Optional.ofNullable(call("tell its status",
                        client -> client.get().uri(statusPath).retrieve().body(statusType)));
            } catch (RuntimeException unanswered) {
                status = Optional.empty();
            }
            statusUntil = now.plus(STATUS_LIFETIME);
        }
        return status;
    }

    /** Makes one call; {@code operation} says what was asked, as in "read the changes". */
    protected <T> T call(String operation, Function<RestClient, T> request) {
        long started = System.nanoTime();
        try {
            T answer = request.apply(client);
            log.debug("{} answered to {} in {} ms", service, operation, millisSince(started));
            return answer;
        } catch (RuntimeException e) {
            RuntimeException failure = failure(operation, e);
            log.warn("{} after {} ms, status {}", failure.getMessage(), millisSince(started), statusOf(e));
            throw failure;
        }
    }

    /** Makes one call that answers a list; no answer is an empty list. */
    protected <T> List<T> list(String operation, Function<RestClient, List<T>> request) {
        return emptyIfNull(call(operation, request));
    }

    /** The answer, which must carry a value such as the number of a new change; without it the call failed. */
    protected <A> A required(A answer, Function<A, String> value, String what) {
        if (answer == null || isBlank(value.apply(answer))) {
            log.warn("{} answered without the {}", service, what);
            String message = service + " answered without the " + what;
            throw new UncheckedIOException(message, new IOException(message));
        }
        return answer;
    }

    private RuntimeException failure(String operation, RuntimeException e) {
        return switch (e) {
            case RestClientResponseException response -> refusal(operation, response);
            case RestClientException unanswered ->
                    unavailable(service + " did not answer when asked to " + operation, reasonOf(unanswered), e);
            default -> unavailable(service + " did not answer when asked to " + operation,
                    "the call could not be sent: " + reasonOf(e), e);
        };
    }

    private RuntimeException refusal(String operation, RestClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        Problem problem = problemOf(e);
        String detail = problem == null ? null : problem.detail();
        if (status.is5xxServerError()) {
            return unavailable(service + " failed to " + operation,
                    status.value() + " " + defaultIfBlank(detail, e.getStatusText()), e);
        }
        String refused = defaultIfBlank(detail, service + " refused to " + operation + " (" + status.value() + ")");
        return switch (status.value()) {
            case 400 -> problem == null || emptyIfNull(problem.problems()).isEmpty()
                    ? new IllegalStateException(refused) : new InvalidRequestException(problem.problems());
            case 401, 403 -> unavailable(service + " refused the service token of Beadle-GUI when asked to "
                    + operation, "BEADLE_SERVICE_TOKEN must be the same in the three services", e);
            case 404 -> new NoSuchElementException(refused);
            default -> new IllegalStateException(refused);
        };
    }

    private static Problem problemOf(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(Problem.class);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private static Object statusOf(RuntimeException e) {
        return e instanceof RestClientResponseException response ? response.getStatusCode().value() : "none";
    }

    private static long millisSince(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private UncheckedIOException unavailable(String what, String reason, RuntimeException cause) {
        return new UncheckedIOException(what + ": " + truncate(masked(reason), MAX_REASON), new IOException(cause));
    }

    private static String reasonOf(RuntimeException e) {
        Throwable root = getRootCause(e);
        Throwable reason = root == null ? e : root;
        return defaultIfBlank(reason.getMessage(), reason.getClass().getSimpleName());
    }

    /** The reason without the service token, which a refused header value, for example, would quote. */
    private String masked(String reason) {
        return token.isEmpty() ? reason : CS.replace(reason, token, Secrets.MASK);
    }

    /** The {@code application/problem+json} answer of a refusal, with the field problems of a {@code 400}. */
    record Problem(String detail, List<FieldProblem> problems) {
    }
}
