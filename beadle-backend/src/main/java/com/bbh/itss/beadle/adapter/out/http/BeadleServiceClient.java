package com.bbh.itss.beadle.adapter.out.http;

import com.bbh.itss.beadle.domain.shared.InvalidRequestException;
import com.bbh.itss.beadle.domain.shared.InvalidRequestException.FieldProblem;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

import static java.time.Duration.ofSeconds;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.apache.commons.lang3.StringUtils.replace;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * A client of Beadle-Internal or Beadle-ProTech that calls the way docs/CONTRACTS.md has it: the service token in
 * {@code Authorization}, the caller in {@code X-Beadle-User} and {@code X-Correlation-Id} ({@link CallerHeaders}),
 * 5 seconds to connect and 30 to answer. A service that cannot be reached or answers {@code 5xx} fails the call with
 * an {@link UncheckedIOException} naming the service, which the API answers {@code 503}, and so does a call the HTTP
 * layer could not even send (an {@link IllegalArgumentException} for a header value it refuses, for example); a
 * refusal ({@code 4xx}) keeps its meaning: the field problems of a {@code 400}, not found for {@code 404}, a conflict
 * otherwise. The token is sent trimmed, as the three services compare it, and never appears in a message.
 */
@Accessors(fluent = true)
public class BeadleServiceClient {

    public static final Duration CONNECT_TIMEOUT = ofSeconds(5);
    public static final Duration READ_TIMEOUT = ofSeconds(30);
    public static final Duration STATUS_LIFETIME = ofSeconds(30);
    public static final int MAX_REASON = 300;

    static final String MASK = "*****";

    @Getter
    private final String service;
    private final String token;
    private final RestClient client;

    protected BeadleServiceClient(String service, String url, String token, RestClient.Builder builder,
                                  CallerHeaders caller) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.service = service;
        this.token = trimToEmpty(token);
        this.client = builder.baseUrl(removeEnd(trimToEmpty(url), "/")).requestFactory(requestFactory)
                .defaultHeader(AUTHORIZATION, "Bearer " + this.token)
                .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE)
                .requestInterceptor(caller).build();
    }

    /** Makes one call; {@code operation} says what was asked, as in "read the changes". */
    protected <T> T call(String operation, Function<RestClient, T> request) {
        try {
            return request.apply(client);
        } catch (RestClientResponseException e) {
            throw refusal(operation, e);
        } catch (RestClientException e) {
            throw unavailable(service + " did not answer when asked to " + operation, reasonOf(e), e);
        } catch (RuntimeException e) {
            throw unavailable(service + " did not answer when asked to " + operation,
                    "the call could not be sent: " + reasonOf(e), e);
        }
    }

    RuntimeException refusal(String operation, RestClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        Problem problem = problemOf(e);
        String detail = problem == null ? null : problem.detail();
        if (status.is5xxServerError()) {
            return unavailable(service + " failed to " + operation,
                    status.value() + " " + defaultIfBlank(detail, e.getStatusText()), e);
        }
        String refused = defaultIfBlank(detail, service + " refused to " + operation + " (" + status.value() + ")");
        return switch (status.value()) {
            case 400 -> problem == null || problem.fieldProblems().isEmpty() ? new IllegalStateException(refused)
                    : new InvalidRequestException(problem.fieldProblems());
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
        return token.isEmpty() ? reason : replace(reason, token, MASK);
    }

    /** The {@code application/problem+json} answer of a refusal, with the field problems of a {@code 400}. */
    record Problem(String title, String detail, List<FieldProblem> problems, List<FieldProblem> errors) {

        List<FieldProblem> fieldProblems() {
            return problems == null ? emptyIfNull(errors) : problems;
        }
    }
}
