package com.bbh.itss.beadle.domain.catalog;

import com.bbh.itss.beadle.domain.shared.InvalidRequestException;
import com.bbh.itss.beadle.domain.shared.ValidationProblems;

import java.util.Optional;
import java.util.function.Function;

import static com.bbh.itss.beadle.domain.shared.Versions.requireReadAt;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trim;

public record BusinessService(Long id, String name, long version) {

    public static final int MAX_NAME_LENGTH = 100;

    public BusinessService {
        name = trim(name);
    }

    public static BusinessService create(String name, Function<String, Optional<BusinessService>> byName) {
        return new BusinessService(null, name, 0).checked(byName);
    }

    public BusinessService rename(Long expectedVersion, String name, Function<String, Optional<BusinessService>> byName) {
        requireReadAt(expectedVersion, version);
        return new BusinessService(id, name, version).checked(byName);
    }

    private BusinessService checked(Function<String, Optional<BusinessService>> byName) {
        if (isBlank(name)) {
            throw InvalidRequestException.of("name", "must not be blank");
        }
        new ValidationProblems().fits("name", name, MAX_NAME_LENGTH).throwIfAny();
        byName.apply(name).filter(other -> !other.id().equals(id)).ifPresent(other -> {
            throw new IllegalStateException("A business service named " + other.name() + " already exists");
        });
        return this;
    }
}
