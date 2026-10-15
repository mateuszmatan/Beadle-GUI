package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.Mirrors;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.CODE_PATTERN;
import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.CODE_RULE;
import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.NAME_MAX;
import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.OWNER_TEAM_MAX;

public record ProductRequest(@Pattern(regexp = CODE_PATTERN, message = CODE_RULE) String code,
                             @NotBlank @Size(max = NAME_MAX) String name, Long departmentId,
                             @Size(max = OWNER_TEAM_MAX) String ownerTeam,
                             @Email @Size(max = 320) String contactEmail, Long version)
        implements Mirrors<ProductCommand> {
}
