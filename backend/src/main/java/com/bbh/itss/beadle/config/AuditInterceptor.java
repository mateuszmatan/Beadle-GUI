package com.bbh.itss.beadle.config;

import com.bbh.itss.beadle.adapter.Secrets;
import com.bbh.itss.beadle.application.AuditNotes;
import com.bbh.itss.beadle.application.AuditNotes.Listening;
import com.bbh.itss.beadle.application.AuditNotes.Stored;
import com.bbh.itss.beadle.application.Audited;
import com.bbh.itss.beadle.application.AuditedKey;
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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.bbh.itss.beadle.domain.audit.ActionOutcome.FAILURE;
import static com.bbh.itss.beadle.domain.audit.ActionOutcome.SUCCESS;
import static com.bbh.itss.beadle.domain.shared.Text.abbreviateBytes;
import static com.bbh.itss.beadle.domain.shared.Text.bytes;
import static com.bbh.itss.beadle.domain.shared.Timestamps.now;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;
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
 * <p>
 * {@code DETAILS} holds the arguments by parameter name and, last, what the use case noted with {@link AuditNotes}:
 * {@code "result": {"reminded": [...], "created": ["CTASK0041003", ...]}}, kept whole when long arguments are trimmed
 * to {@link ActionRecord#DETAILS_MAX}. A call that fails also records what it noted it had
 * {@link AuditNotes#storedOnTheWay stored on its way}, each in a {@code SUCCESS} row of its own before its
 * {@code FAILURE} row.
 */
@Slf4j
@RequiredArgsConstructor
class AuditInterceptor implements MethodInterceptor {

    static final String ROLLED_BACK = "The action ended, but its transaction did not commit";
    static final String UNKNOWN_USER = SignedInUserPort.UNKNOWN;

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
             Listening notes = AuditNotes.listen()) {
            ActionRecordBuilder row = ActionRecord.builder().at(now(clock.getIfAvailable(Clock::systemUTC)))
                    .user(user()).action(action).correlationId(CallContext.correlationId().orElse(null));
            String argumentDetails = details(method, arguments);
            ActionObject removed = action.deletes() ? keyed(fromArguments(action, method, arguments)) : null;
            long started = System.nanoTime();
            Object result;
            try {
                result = invocation.proceed();
            } catch (Throwable failure) {
                row.durationMs(NANOSECONDS.toMillis(System.nanoTime() - started))
                        .details(withResult(argumentDetails, notes));
                if (audited.onlyWhenNoted()) {
                    failedOnNoted(row.build(), notes.noted(), failure);
                } else {
                    storedOnTheWay(row.build().toBuilder().details(argumentDetails).outcome(SUCCESS).build(),
                            notes.storedOnTheWay());
                    inOwnTransaction(row.outcome(FAILURE).message(messageOf(failure)),
                            () -> removed != null ? removed : fromArguments(action, method, arguments));
                }
                throw failure;
            }
            row.durationMs(NANOSECONDS.toMillis(System.nanoTime() - started)).outcome(SUCCESS)
                    .details(withResult(argumentDetails, notes));
            if (audited.onlyWhenNoted()) {
                ActionRecord each = row.build();
                notes.noted().forEach(object -> succeeded(each.toBuilder(), () -> object));
            } else {
                succeeded(row, () -> removed != null ? removed : objectOf(action, method, arguments, result));
            }
            return result;
        }
    }

    /**
     * The rows of a conditional action that failed: {@code SUCCESS} for every object it was done with, and
     * {@code FAILURE} for the one noted last, which it was busy with; none when it took place on nothing.
     */
    private void failedOnNoted(ActionRecord row, List<ActionObject> noted, Throwable failure) {
        for (int index = 0; index < noted.size(); index++) {
            ActionObject object = noted.get(index);
            ActionRecordBuilder each = index < noted.size() - 1 ? row.toBuilder().outcome(SUCCESS)
                    : row.toBuilder().outcome(FAILURE).message(messageOf(failure));
            inOwnTransaction(each, () -> object);
        }
    }

    /**
     * The rows of what a failed call stored on its way to its own action, such as the newer state of a change it read
     * from ProTech before it was refused: a {@code SUCCESS} row of the action noted for each, with the arguments of the
     * call but not its result, which belongs to the {@code FAILURE} row that follows.
     */
    private void storedOnTheWay(ActionRecord row, List<Stored> stored) {
        stored.forEach(each -> inOwnTransaction(row.toBuilder().action(each.action()), each::object));
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
    static ActionObject objectOf(Action action, Method method, Object[] arguments, Object result) {
        Object value = result instanceof Optional<?> optional ? optional.orElse(null) : result;
        return value instanceof AuditedObject audited ? audited.auditedObject()
                : fromArguments(action, method, arguments);
    }

    /**
     * A first argument of type long is the id of the action's object; else an argument that names its object; else
     * the {@link AuditedKey} argument its key, such as the name of a department that could not be created.
     */
    static ActionObject fromArguments(Action action, Method method, Object[] arguments) {
        if (arguments.length > 0 && arguments[0] instanceof Long id) {
            return ActionObject.of(action.objectType(), id);
        }
        return Arrays.stream(arguments).filter(AuditedObject.class::isInstance)
                .map(argument -> ((AuditedObject) argument).auditedObject()).findFirst()
                .orElseGet(() -> ActionObject.keyed(action.objectType(), auditedKey(method, arguments)));
    }

    private static String auditedKey(Method method, Object[] arguments) {
        Parameter[] parameters = method.getParameters();
        for (int index = 0; index < parameters.length && index < arguments.length; index++) {
            if (parameters[index].isAnnotationPresent(AuditedKey.class) && arguments[index] != null) {
                return trimToNull(arguments[index].toString());
            }
        }
        return null;
    }

    /**
     * The details of the arguments with what the use case noted appended as {@code "result"}, its {@code "created"}
     * keys last, where the object-key filter of the audit trail looks for them. Arguments too long for
     * {@link ActionRecord#DETAILS_MAX} are trimmed before the result, so the result stays whole.
     */
    String withResult(String argumentDetails, Listening notes) {
        Map<String, Object> results = notes.results();
        List<String> created = notes.created();
        if (results.isEmpty() && created.isEmpty()) {
            return argumentDetails;
        }
        try {
            JsonMapper mapper = json.getIfUnique(() -> JsonMapper.builder().build());
            ObjectNode result = mapper.createObjectNode();
            results.forEach((name, value) -> result.set(name, Secrets.masked(name, mapper.valueToTree(value))));
            if (!created.isEmpty()) {
                result.remove(ActionRecord.CREATED);
                result.set(ActionRecord.CREATED, mapper.valueToTree(created));
            }
            String suffix = "\"" + ActionRecord.RESULT + "\":" + mapper.writeValueAsString(result) + "}";
            String arguments = argumentDetails == null || argumentDetails.length() < 2 ? "{"
                    : argumentDetails.substring(0, argumentDetails.length() - 1);
            String separator = arguments.equals("{") ? "" : ",";
            int room = ActionRecord.DETAILS_MAX - bytes(suffix) - bytes(separator);
            return abbreviateBytes(arguments, Math.max(room, 1)) + separator + suffix;
        } catch (RuntimeException e) {
            log.warn("The result of an action could not be written as JSON: {}", e.getMessage());
            return argumentDetails;
        }
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

    /** The arguments by parameter name as JSON, every property that looks like a secret masked ({@link Secrets}). */
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
                    details.set(name, Secrets.masked(name, mapper.valueToTree(arguments[index])));
                }
            }
            return mapper.writeValueAsString(details);
        } catch (RuntimeException e) {
            log.warn("The arguments of {} could not be written as JSON: {}", method.getName(), e.getMessage());
            return null;
        }
    }

    private static Method implementationOf(MethodInvocation invocation) {
        Object target = invocation.getThis();
        return target == null ? invocation.getMethod()
                : AopUtils.getMostSpecificMethod(invocation.getMethod(), AopUtils.getTargetClass(target));
    }
}
