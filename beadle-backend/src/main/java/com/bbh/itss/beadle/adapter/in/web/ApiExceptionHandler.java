package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.domain.shared.InvalidRequestException;
import com.bbh.itss.beadle.domain.shared.InvalidRequestException.FieldProblem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.NoSuchElementException;

import static com.bbh.itss.beadle.domain.shared.Failures.STALE_VERSION;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.ProblemDetail.forStatusAndDetail;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** The member of a problem that lists the fields of a refused request, as the contract of the services names it. */
    static final String PROBLEMS = "problems";

    /** What refuses a request before the controller calls its use case, which therefore leaves no row of its own. */
    static final List<Class<? extends Exception>> REFUSED_BEFORE_USE_CASE = List.of(
            HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class, TypeMismatchException.class, ServletRequestBindingException.class,
            HttpMediaTypeNotSupportedException.class);

    private final RefusedRequests refusals;

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return problem(NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    ProblemDetail forbidden(SecurityException e) {
        return problem(FORBIDDEN, "Forbidden", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException e) {
        return problem(CONFLICT, "Conflict", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail staleData(OptimisticLockingFailureException e) {
        return problem(CONFLICT, "Conflict", STALE_VERSION);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        return problem(CONFLICT, "Conflict",
                "The change violates a database constraint, most likely a duplicate name or key.");
    }

    @ExceptionHandler(UncheckedIOException.class)
    ProblemDetail unavailable(UncheckedIOException e) {
        return problem(SERVICE_UNAVAILABLE, "Service Unavailable", e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalid(InvalidRequestException e) {
        ProblemDetail detail = problem(BAD_REQUEST, "Validation failed", e.getMessage());
        detail.setProperty(PROBLEMS, e.problems());
        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Beadle could not serve a request", e);
        return problem(INTERNAL_SERVER_ERROR, "Request failed",
                "Beadle could not handle the request. The failure is in Beadle's log.");
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        MalformedBody body = MalformedBody.of(e);
        ProblemDetail detail = problem(BAD_REQUEST, "Malformed request", body.detail());
        if (!body.problems().isEmpty()) {
            detail.setProperty(PROBLEMS, body.problems());
        }
        return handleExceptionInternal(e, detail, headers, BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail detail = invalid(new InvalidRequestException(e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldProblem(error.getField(), error.getDefaultMessage()))
                .toList()));
        return handleExceptionInternal(e, detail, headers, BAD_REQUEST, request);
    }

    /**
     * A path or query value that cannot be converted, such as {@code action=FOO} on the audit trail: answered with the
     * parameter under {@code problems}, as a body value that cannot be read is.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException e, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String name = nameOf(e);
        String given = name == null ? "a value of this request" : "'" + name + "'";
        ProblemDetail detail = problem(BAD_REQUEST, "Malformed request",
                "The value given for " + given + " is not one this endpoint can read.");
        if (name != null) {
            detail.setProperty(PROBLEMS,
                    List.of(new FieldProblem(name, MalformedBody.reasonFor(e.getRequiredType()))));
        }
        return handleExceptionInternal(e, detail, headers, BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(e, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail detail && status.is4xxClientError()
                && REFUSED_BEFORE_USE_CASE.stream().anyMatch(type -> type.isInstance(e))) {
            refusals.record(request, detail);
        }
        return response;
    }

    private static String nameOf(TypeMismatchException e) {
        return e instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : e.getPropertyName();
    }

    static ProblemDetail problem(HttpStatus status, String title, String message) {
        ProblemDetail detail = forStatusAndDetail(status, message);
        detail.setTitle(title);
        return detail;
    }
}
