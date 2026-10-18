package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.adapter.AuditDetails;
import com.bbh.itss.beadle.adapter.Secrets;
import com.bbh.itss.beadle.application.audit.port.in.ActionHistoryUseCase;
import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.ActionRecord;
import com.bbh.itss.beadle.domain.audit.ActionRecord.ActionRecordBuilder;
import com.bbh.itss.beadle.domain.audit.ObjectType;
import com.bbh.itss.beadle.domain.shared.InvalidRequestException;
import com.bbh.itss.beadle.domain.shared.InvalidRequestException.FieldProblem;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.LongNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static com.bbh.itss.beadle.domain.audit.ActionOutcome.FAILURE;
import static com.bbh.itss.beadle.domain.shared.Timestamps.now;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNumeric;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.ProblemDetail.forStatusAndDetail;
import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;
import static org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE;
import static org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;

/**
 * Files what Beadle refused before a use case ran as a {@code FAILURE} row of the audit trail. A request to an
 * {@link AuditedRequest} endpoint is filed under the action of the endpoint and the object its path or body names,
 * with the path values and the body as sent ({@link KeptRequestBodies}) with their secrets masked, and the field
 * problems of the answer as the message, or its detail when it names no field. A request the security filters refused,
 * a signed-in user's change for its CSRF token or a callback with the service token for its correlation id, is filed
 * the same way, without the body nobody read. A caller who proved nothing, without a session or the service token, or
 * whose sign-in at Ping failed, is only logged, so that nobody can fill the audit trail anonymously. Such a row takes
 * no time of a use case, so its duration is 0. A row that cannot be written is logged and does not change the answer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefusedRequests {

    static final String KEPT_BODY = RefusedRequests.class.getName() + ".BODY";
    static final String ID = "id";
    static final String NO_CSRF_TOKEN = "The change needs the token of the XSRF-TOKEN cookie in X-XSRF-TOKEN.";

    private final ActionHistoryUseCase history;
    private final JsonMapper json;
    private final Clock clock;
    private final ObjectProvider<RequestMappingHandlerMapping> mappings;
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();
    private final AuthenticationTrustResolver trust = new AuthenticationTrustResolverImpl();

    /** Records a request the security filters refused before Spring MVC chose its endpoint, which it looks up. */
    public void record(HttpServletRequest request, ProblemDetail problem) {
        if (!ServletRequestPathUtils.hasParsedRequestPath(request)) {
            ServletRequestPathUtils.parseAndCache(request);
        }
        if (mappings.orderedStream().anyMatch(mapping -> handles(mapping, request))) {
            record(new ServletWebRequest(request), problem);
        }
    }

    /**
     * Answers {@code 403} as Spring does once the request is filed, when a user is signed in: what the users' chain
     * denies is a CSRF token.
     */
    public AccessDeniedHandler forbidden() {
        AccessDeniedHandler forbidden = new AccessDeniedHandlerImpl();
        return (request, response, denied) -> {
            String why = denied instanceof CsrfException ? NO_CSRF_TOKEN : denied.getMessage();
            if (trust.isAuthenticated(SecurityContextHolder.getContext().getAuthentication())) {
                record(request, forStatusAndDetail(FORBIDDEN, why));
            } else {
                log.warn("Refused {} {} without a session: {}", request.getMethod(), request.getRequestURI(), why);
            }
            forbidden.handle(request, response, denied);
        };
    }

    /** Logs a sign-in that failed at Ping, or on its way back, with the error, then lets the page say it failed. */
    public AuthenticationFailureHandler signInFailures(AuthenticationFailureHandler page) {
        return (request, response, failure) -> {
            log.warn("A sign-in with Ping failed: {}", failure instanceof OAuth2AuthenticationException oauth
                    ? oauth.getError().toString() : failure.getMessage());
            page.onAuthenticationFailure(request, response, failure);
        };
    }

    /** Records the refusal of a request, when it was for an audited endpoint, with the problem it was answered. */
    void record(WebRequest request, ProblemDetail problem) {
        if (!(request.getAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE, SCOPE_REQUEST) instanceof HandlerMethod handler)) {
            return;
        }
        AuditedRequest audited = handler.getMethodAnnotation(AuditedRequest.class);
        if (audited == null) {
            return;
        }
        Map<String, String> variables = pathVariables(request);
        JsonNode body = body(request);
        write(ActionRecord.builder().at(now(clock)).action(audited.value())
                .details(details(handler, variables, body)).outcome(FAILURE).message(messageOf(problem)),
                objectOf(audited, variables, body));
    }

    private void write(ActionRecordBuilder row, ActionObject object) {
        try {
            history.record(row, object);
        } catch (RuntimeException e) {
            log.warn("A refused {} could not be written to the audit trail: {}", row.build().action(), e.getMessage());
        }
    }

    /** Whether the mapping has an endpoint for the request, which it then notes in the request as Spring MVC does. */
    private static boolean handles(RequestMappingHandlerMapping mapping, HttpServletRequest request) {
        try {
            return mapping.getHandler(request) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** The object the path names by its {@code {id}}, else the one the body names, else none. */
    static ActionObject objectOf(AuditedRequest audited, Map<String, String> variables, JsonNode body) {
        ObjectType type = audited.value().objectType();
        if (variables.containsKey(ID)) {
            return ActionObject.of(type, idOf(StringNode.valueOf(variables.get(ID))));
        }
        ObjectType bodyType = audited.objectType().length > 0 ? audited.objectType()[0] : type;
        if (!audited.objectId().isEmpty()) {
            return ActionObject.of(bodyType, idOf(property(body, audited.objectId())));
        }
        if (!audited.objectKey().isEmpty()) {
            JsonNode key = property(body, audited.objectKey());
            return ActionObject.keyed(bodyType, key != null && key.isValueNode() ? trimToNull(key.asString()) : null);
        }
        return ActionObject.of(type, null);
    }

    private static JsonNode property(JsonNode body, String name) {
        return body == null ? null : body.get(name);
    }

    private static Long idOf(JsonNode value) {
        if (value == null) {
            return null;
        }
        if (value.isIntegralNumber() && value.canConvertToLong()) {
            return value.longValue();
        }
        String text = value.isString() ? value.asString().trim() : "";
        return isNumeric(text) && text.length() < 19 ? Long.valueOf(text) : null;
    }

    /**
     * The path values by name (numbers as numbers) and the body under the name of its parameter, secrets masked, as
     * JSON that {@link AuditDetails} keeps within the {@code DETAILS} of the row.
     */
    String details(HandlerMethod handler, Map<String, String> variables, JsonNode body) {
        try {
            ObjectNode details = json.createObjectNode();
            variables.forEach((name, value) -> {
                Long number = idOf(StringNode.valueOf(value));
                details.set(name, number == null ? StringNode.valueOf(value) : LongNode.valueOf(number));
            });
            String bodyName = bodyNameOf(handler);
            if (bodyName != null) {
                details.set(bodyName, body == null ? NullNode.getInstance() : body);
            }
            Secrets.masked("", details);
            return AuditDetails.write(json, details);
        } catch (RuntimeException e) {
            log.warn("The refused request could not be written as JSON: {}", e.getMessage());
            return null;
        }
    }

    private String bodyNameOf(HandlerMethod handler) {
        String[] names = parameterNames.getParameterNames(handler.getMethod());
        for (MethodParameter parameter : handler.getMethodParameters()) {
            if (parameter.hasParameterAnnotation(RequestBody.class)) {
                int index = parameter.getParameterIndex();
                return names == null ? "body" : names[index];
            }
        }
        return null;
    }

    /** The body as the client sent it, when it was JSON; {@code null} when there was none or it was no JSON. */
    private JsonNode body(WebRequest request) {
        if (!(request.getAttribute(KEPT_BODY, SCOPE_REQUEST) instanceof byte[] kept) || kept.length == 0) {
            return null;
        }
        try {
            return json.readTree(kept);
        } catch (JacksonException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> pathVariables(WebRequest request) {
        Object variables = request.getAttribute(URI_TEMPLATE_VARIABLES_ATTRIBUTE, SCOPE_REQUEST);
        return variables instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }

    /** The field problems as the audit interceptor writes those of a refused use case, else the detail. */
    static String messageOf(ProblemDetail problem) {
        Map<String, Object> properties = problem.getProperties();
        if (properties != null && properties.get(ApiExceptionHandler.PROBLEMS) instanceof List<?> problems
                && !problems.isEmpty()) {
            return InvalidRequestException.summary(problems.stream().map(FieldProblem.class::cast).toList());
        }
        return defaultIfBlank(problem.getDetail(), problem.getTitle());
    }
}
