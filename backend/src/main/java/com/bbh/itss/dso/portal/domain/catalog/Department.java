package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConflictException;
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.Versions;

import java.util.Optional;
import java.util.function.Function;

public record Department(Long id, String name, long version) {

    private static final int MAX_NAME_LENGTH = 100;

    public Department {
        name = name == null ? null : name.trim();
    }

    public static Department create(String name, Function<String, Optional<Department>> byName) {
        return new Department(null, name, 0).checked(byName);
    }

    public Department rename(Long expectedVersion, String name, Function<String, Optional<Department>> byName) {
        Versions.requireCurrent(expectedVersion, version);
        return new Department(id, name, version).checked(byName);
    }

    private Department checked(Function<String, Optional<Department>> byName) {
        if (Text.isBlank(name)) {
            throw InvalidRequestException.of("name", "must not be blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw InvalidRequestException.of("name", "must be at most " + MAX_NAME_LENGTH + " characters");
        }
        byName.apply(name).filter(other -> !other.id().equals(id)).ifPresent(other -> {
            throw new ConflictException("A department named " + other.name() + " already exists");
        });
        return this;
    }
}
