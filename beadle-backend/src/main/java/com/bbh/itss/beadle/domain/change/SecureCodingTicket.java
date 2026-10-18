package com.bbh.itss.beadle.domain.change;

import com.bbh.itss.beadle.domain.shared.ValidationProblems;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static com.bbh.itss.beadle.domain.change.ChangeTemplate.REQUIRED;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.time.format.ResolverStyle.STRICT;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/** What CyberTrack needs to scan a release; the form asks for the implementation date as {@code MMDDYYYY}. */
public record SecureCodingTicket(String changeNumber, String applicationName, LocalDate implementationDate,
                                 SecureCoding inputs) {

    public static final String DATE_MESSAGE = "must be a date written MMDDYYYY, such as 10152026";

    private static final DateTimeFormatter DATE = ofPattern("MMdduuuu").withResolverStyle(STRICT);

    public static SecureCodingTicket of(ProductionChange change, SecureCoding inputs, String implementationDate) {
        ValidationProblems problems = new ValidationProblems();
        String text = trimToNull(implementationDate);
        LocalDate date = text == null ? null : dateOf(text);
        inputs.require(problems);
        problems.require("implementationDate", text, REQUIRED);
        if (text != null && date == null) {
            problems.add("implementationDate", DATE_MESSAGE);
        }
        problems.throwIfAny();
        return new SecureCodingTicket(change.number(), change.productName(), date, inputs);
    }

    private static LocalDate dateOf(String text) {
        try {
            return text.length() == 8 ? LocalDate.parse(text, DATE) : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
