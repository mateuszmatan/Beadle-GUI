package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.Mirrors;
import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChangeProfileRequest(Long version, @NotNull @Valid TemplateDto template) {

    ChangeTemplate toTemplate() {
        return RecordMapper.map(template, ChangeTemplate.class);
    }

    public record TemplateDto(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,9}$",
                    message = "must be a Jira project key such as CERT: 2 to 10 upper case letters, digits or _")
            String jiraProjectKey,
            @NotBlank @Size(max = 200) String configurationItem,
            @NotBlank @Size(max = 200) String assignmentGroup,
            @NotNull ChangeTemplate.Type type,
            @NotBlank @Size(max = 100) String category,
            @NotNull ChangeTemplate.Risk risk,
            @NotNull ChangeTemplate.Impact impact,
            @NotBlank @Size(max = 2000) String riskAssessment,
            @NotEmpty(message = "add at least one manager who approves the change") @Size(max = 10)
            List<@NotBlank @Size(max = 200) String> approvers,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 2000) String implementationPlan,
            @NotBlank @Size(max = 2000) String backoutPlan,
            @NotBlank @Size(max = 2000) String testPlan) implements Mirrors<ChangeTemplate> {
    }
}
