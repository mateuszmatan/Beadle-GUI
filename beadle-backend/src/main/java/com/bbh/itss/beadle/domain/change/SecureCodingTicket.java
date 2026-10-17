package com.bbh.itss.beadle.domain.change;

import com.bbh.itss.beadle.domain.shared.ValidationProblems;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static com.bbh.itss.beadle.domain.change.ChangeTemplate.REQUIRED;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.time.format.ResolverStyle.STRICT;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record SecureCodingTicket(String changeNumber, String applicationName, String implementationDate,
                                 SecureCoding inputs) {

    public static final String DATE_MESSAGE = "must be a date written MMDDYYYY, such as 10152026";

    private static final DateTimeFormatter DATE = ofPattern("MMdduuuu").withResolverStyle(STRICT);

    public static SecureCodingTicket of(ProductionChange change, SecureCoding inputs, String implementationDate) {
        ValidationProblems problems = new ValidationProblems();
        String date = trimToNull(implementationDate);
        inputs.require(problems);
        problems.require("implementationDate", date, REQUIRED);
        if (date != null && !isDate(date)) {
            problems.add("implementationDate", DATE_MESSAGE);
        }
        problems.throwIfAny();
        return new SecureCodingTicket(change.number(), change.productName(), date, inputs);
    }

    private static boolean isDate(String date) {
        try {
            DATE.parse(date);
            return date.length() == 8;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
