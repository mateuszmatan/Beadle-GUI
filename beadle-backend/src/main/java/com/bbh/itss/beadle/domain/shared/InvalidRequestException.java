package com.bbh.itss.beadle.domain.shared;

import lombok.Getter;

import java.util.List;

import static java.util.stream.Collectors.joining;

@Getter
public class InvalidRequestException extends IllegalArgumentException {

    private final List<FieldProblem> problems;

    public InvalidRequestException(List<FieldProblem> problems) {
        super(problems.size() == 1 ? problems.get(0).message() : problems.size() + " fields are invalid");
        this.problems = List.copyOf(problems);
    }

    public static InvalidRequestException of(String field, String message) {
        return new InvalidRequestException(List.of(new FieldProblem(field, message)));
    }

    /** The problems as one line, {@code "name must not be blank; code is too long"}, as the audit trail keeps them. */
    public static String summary(List<FieldProblem> problems) {
        return problems.stream().map(problem -> problem.field() + " " + problem.message()).collect(joining("; "));
    }

    public record FieldProblem(String field, String message) {
    }
}
