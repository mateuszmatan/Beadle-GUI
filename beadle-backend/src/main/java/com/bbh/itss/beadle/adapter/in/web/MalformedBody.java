package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.domain.shared.InvalidRequestException.FieldProblem;
import org.apache.commons.lang3.ClassUtils;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.Strings.CS;

record MalformedBody(String detail, List<FieldProblem> problems) {

    private static final String MISSING = "Required request body is missing";
    private static final String CANNOT_HOLD = "has a value this field cannot hold";
    private static final Set<Class<?>> WHOLE_NUMBERS = Set.of(Integer.class, Long.class, Short.class, Byte.class,
            BigInteger.class);

    static MalformedBody of(HttpMessageNotReadableException e) {
        if (CS.startsWith(e.getMessage(), MISSING)) {
            return new MalformedBody("The request body is missing.", List.of());
        }
        if (e.getCause() instanceof DatabindException databind) {
            return ofValue(databind);
        }
        if (e.getCause() instanceof JacksonException) {
            return new MalformedBody("The request body is not valid JSON.", List.of());
        }
        return new MalformedBody("The request body could not be read.", List.of());
    }

    private static MalformedBody ofValue(DatabindException e) {
        String field = fieldOf(e);
        if (field.isEmpty()) {
            return new MalformedBody("The request body does not have the shape this endpoint expects.", List.of());
        }
        String reason = reasonOf(e);
        return new MalformedBody(field + " " + reason, List.of(new FieldProblem(field, reason)));
    }

    private static String reasonOf(DatabindException e) {
        return e instanceof InvalidFormatException invalid ? reasonFor(invalid.getTargetType()) : CANNOT_HOLD;
    }

    /**
     * Why a value cannot be read as the type of its field or request parameter: the values of an enum, or the form of
     * an instant, a date or a number.
     */
    static String reasonFor(Class<?> type) {
        if (type == null) {
            return CANNOT_HOLD;
        }
        if (type.isEnum()) {
            return "must be one of " + Arrays.stream(type.getEnumConstants()).map(String::valueOf)
                    .collect(joining(", "));
        }
        if (Instant.class.equals(type)) {
            return "must be an instant in UTC, such as 2026-10-15T09:00:00Z";
        }
        if (LocalDate.class.equals(type)) {
            return "must be a date, such as 2026-10-15";
        }
        if (WHOLE_NUMBERS.contains(ClassUtils.primitiveToWrapper(type))) {
            return "must be a whole number";
        }
        if (Boolean.class.equals(ClassUtils.primitiveToWrapper(type))) {
            return "must be true or false";
        }
        return CANNOT_HOLD;
    }

    private static String fieldOf(JacksonException e) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : e.getPath()) {
            if (reference.getPropertyName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
