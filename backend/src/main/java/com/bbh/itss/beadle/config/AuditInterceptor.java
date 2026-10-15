package com.bbh.itss.beadle.config;

import com.bbh.itss.beadle.application.AuditNotes;
import com.bbh.itss.beadle.application.AuditNotes.Listening;
import com.bbh.itss.beadle.application.Audited;
import com.bbh.itss.beadle.application.CallContext;
import com.bbh.itss.beadle.application.CallContext.Scope;
import com.bbh.itss.beadle.application.UseCase;
import com.bbh.itss.beadle.application.audit.port.out.ActionHistoryPort;
import com.bbh.itss.beadle.application.audit.port.out.ObjectKeyPort;
import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort;
import com.bbh.itss.beadle.domain.audit.Action;
import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.ActionRecord;
import com.bbh.itss.beadle.domain.audit.ActionRecord.ActionRecordBuilder;
import com.bbh.itss.beadle.domain.audit.AuditedObject;
import com.bbh.itss.beadle.domain.shared.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.annotation.AnnotationClassFilter;
import org.springframework.aop.support.annotation.AnnotationMethodMatcher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.bbh.itss.beadle.domain.audit.ActionOutcome.FAILURE;
import static com.bbh.itss.beadle.domain.audit.ActionOutcome.SUCCESS;
import static com.bbh.itss.beadle.domain.shared.Timestamps.now;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;
import static org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive;
import static org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly;
import static org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive;
import static org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization;

/**
 * Writes one row of the audit trail for every call of an {@link Audited} use-case method. It runs inside the
 * transaction interceptor, so the row of a successful call joins the use case's read-write transaction (and a
 * {@code FAILURE} row follows if that transaction rolls back after all), while the row of a failed call, and every
 * row of a use case without a transaction, is written in a {@code REQUIRES_NEW} transaction of its own. Rows that
 * cannot be written in their own transaction are logged and do not change the outcome of the use case.
 */
@Slf4j
@RequiredArgsConstructor
class AuditInterceptor implements MethodInterceptor {

    static final String MASK = "*****";
    static final Pattern SECRET = Pattern.compile("token|password|passwd|secret|authorization|credential|api-?key",
            CASE_INSENSITIVE);
    static final String ROLLED_BACK = "The action ended, but its transaction did not commit";
    static final String UNKNOWN_USER = "unknown";

    private final ObjectProvider<ActionHistoryPort> history;
    private final ObjectProvider<ObjectKeyPort> keys;
    private final ObjectProvider<SignedInUserPort> users;
    private final ObjectProvider<JsonMapper> json;
    private final ObjectProvider<PlatformTransactionManager> transactions;
    private final ObjectProvider<Clock> clock;
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    static Pointcut pointcut() {
        return new ComposablePointcut(new AnnotationClassFilter(UseCase.class, true),
                new AnnotationMethodMatcher(Audited.class, true));
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = implementationOf(invocation);
        Audited audited = findMergedAnnotation(method, Audited.class);
        if (audited == null) {
            return invocation.proceed();
        }
        Action action = audited.value();
        Object[] arguments = invocation.getArguments();
        try (Scope correlation = CallContext.correlated();
             Listening notes = audited.onlyWhenNoted() ? AuditNotes.listen() : null) {
            ActionRecordBuilder row = ActionRecord.builder().at(now(clock.getIfAvailable(Clock::systemUTC)))
                    .user(user()).action(action).correlationId(CallContext.correlationId().orElse(null))
                    .details(details(method, arguments));
            ActionObject removed = action.deletes() ? keyed(fromArguments(action, arguments)) : null;
            long started = System.nanoTime();
            Object result;
            try {
                result = invocation.proceed();
            } catch (Throwable failure) {
                if (notes == null || notes.tookPlace()) {
                    row.durationMs(NANOSECONDS.toMillis(System.nanoTime() - started)).outcome(FAILURE)
                            .message(messageOf(failure));
                    inOwnTransaction(row, () -> removed != null ? removed : fromArguments(action, arguments));
                }
                throw failure;
            }
            if (notes == null || notes.tookPlace()) {
                row.durationMs(NANOSECONDS.toMillis(System.nanoTime() - started)).outcome(SUCCESS);
                succeeded(row, () -> removed != null ? removed : objectOf(action, arguments, result));
            }
            return result;
        }
    }

    private void succeeded(ActionRecordBuilder row, Supplier<ActionObject> object) {
        if (!isActualTransactionActive() || isCurrentTransactionReadOnly()) {
            inOwnTransaction(row, object);
            return;
        }
        ActionRecord recorded = about(row, object.get());
        history.getObject().record(recorded);
        if (isSynchronizationActive()) {
            registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        inOwnTransaction(recorded.toBuilder().outcome(FAILURE).message(ROLLED_BACK),
                                () -> new ActionObject(recorded.objectType(), recorded.objectId(),
                                        recorded.objectKey()));
                    }
                }
            });
        }
    }

    private void inOwnTransaction(ActionRecordBuilder row, Supplier<ActionObject> object) {
        try {
            TransactionTemplate own = new TransactionTemplate(transactions.getObject());
            own.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
            own.executeWithoutResult(status -> history.getObject().record(about(row, object.get())));
        } catch (RuntimeException e) {
            ActionRecord lost = row.build();
            log.warn("The {} {} of {} could not be written to the audit trail: {}", lost.outcome(), lost.action(),
                    lost.user(), e.getMessage());
        }
    }

    private ActionRecord about(ActionRecordBuilder row, ActionObject object) {
        ActionObject keyed = keyed(object);
        return row.objectType(keyed.type()).objectId(keyed.id()).objectKey(keyed.key()).build();
    }

    private ActionObject keyed(ActionObject object) {
        return object.lacksKey() ? object.withKey(keys.getObject().keyOf(object.type(), object.id()).orElse(null))
                : object;
    }

    /** The object a call was about: what its result says, else what its arguments say. */
    static ActionObject objectOf(Action action, Object[] arguments, Object result) {
        Object value = result instanceof Optional<?> optional ? optional.orElse(null) : result;
        return value instanceof AuditedObject audited ? audited.auditedObject() : fromArguments(action, arguments);
    }

    /** A first argument of type long is the id of the action's object; else an argument that names its object. */
    static ActionObject fromArguments(Action action, Object[] arguments) {
        if (arguments.length > 0 && arguments[0] instanceof Long id) {
            return ActionObject.of(action.objectType(), id);
        }
        return Arrays.stream(arguments).filter(AuditedObject.class::isInstance)
                .map(argument -> ((AuditedObject) argument).auditedObject()).findFirst()
                .orElseGet(() -> ActionObject.of(action.objectType(), null));
    }

    static String messageOf(Throwable failure) {
        if (failure instanceof InvalidRequestException invalid && !invalid.problems().isEmpty()) {
            return invalid.problems().stream().map(problem -> problem.field() + " " + problem.message())
                    .collect(joining("; "));
        }
        return defaultIfBlank(failure.getMessage(), failure.getClass().getSimpleName());
    }

    private String user() {
        Optional<String> system = CallContext.systemActor();
        if (system.isPresent()) {
            return system.get();
        }
        try {
            return defaultIfBlank(users.getObject().name(), UNKNOWN_USER);
        } catch (RuntimeException e) {
            return UNKNOWN_USER;
        }
    }

    /** The arguments by parameter name as JSON, every property that looks like a secret masked. */
    String details(Method method, Object[] arguments) {
        try {
            JsonMapper mapper = json.getIfUnique(() -> JsonMapper.builder().build());
            String[] names = parameterNames.getParameterNames(method);
            ObjectNode details = mapper.createObjectNode();
            for (int index = 0; index < arguments.length; index++) {
                String name = names == null ? "arg" + index : names[index];
                if (arguments[index] == null) {
                    details.putNull(name);
                } else {
                    details.set(name, masked(name, mapper.valueToTree(arguments[index])));
                }
            }
            return mapper.writeValueAsString(details);
        } catch (RuntimeException e) {
            log.warn("The arguments of {} could not be written as JSON: {}", method.getName(), e.getMessage());
            return null;
        }
    }

    static JsonNode masked(String name, JsonNode node) {
        if (SECRET.matcher(name).find() && !node.isNull()) {
            return StringNode.valueOf(MASK);
        }
        if (node instanceof ObjectNode object) {
            for (Map.Entry<String, JsonNode> property : new ArrayList<>(object.properties())) {
                object.set(property.getKey(), masked(property.getKey(), property.getValue()));
            }
        } else if (node.isArray()) {
            node.forEach(element -> masked("", element));
        }
        return node;
    }

    private static Method implementationOf(MethodInvocation invocation) {
        Object target = invocation.getThis();
        return target == null ? invocation.getMethod()
                : AopUtils.getMostSpecificMethod(invocation.getMethod(), AopUtils.getTargetClass(target));
    }
}
