package com.bbh.itss.beadle.adapter.out.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

import static com.bbh.itss.beadle.domain.shared.Text.clean;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isBlank;

/** Stores a list of texts, such as the epic keys of a change, one per line, trimmed, without blanks or repeats. */
@Converter(autoApply = true)
public class DelimitedListConverter implements AttributeConverter<List<String>, String> {

    static final String DELIMITER = "\n";

    @Override
    public String convertToDatabaseColumn(List<String> values) {
        List<String> cleaned = clean(values);
        return cleaned.isEmpty() ? null : String.join(DELIMITER, cleaned);
    }

    @Override
    public List<String> convertToEntityAttribute(String column) {
        return isBlank(column) ? List.of() : clean(asList(column.split(DELIMITER)));
    }
}
