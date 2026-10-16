package com.bbh.itss.beadle.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.bbh.itss.beadle.domain.catalog.Department.MAX_NAME_LENGTH;

public record DepartmentRequest(@NotBlank @Size(max = MAX_NAME_LENGTH) String name, Long version) {
}
