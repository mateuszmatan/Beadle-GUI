package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.Mirrors;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Planning;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedUser;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.RiskAssessment;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing;
import com.bbh.itss.dso.portal.domain.change.TaskText;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.bbh.itss.dso.portal.adapter.RecordMapper.map;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.JIRA_KEY;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.JIRA_KEY_MESSAGE;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.MAX_HOURS;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.MAX_PRIVILEGED_USERS;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.TIME_OF_DAY;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.TIME_OF_DAY_MESSAGE;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.change.TaskText.MAX_TASKS;
import static jakarta.validation.constraints.Pattern.Flag.CASE_INSENSITIVE;

public record ChangeProfileRequest(Long version, @NotNull @Valid TemplateDto template,
                                   @NotNull @Size(min = 1, max = MAX_TASKS) List<@NotNull @Valid TaskTextDto> tasks) {

    ChangeTemplate toTemplate() {
        return map(template, ChangeTemplate.class);
    }

    List<TaskText> toTasks() {
        return tasksOf(tasks);
    }

    static List<TaskText> tasksOf(List<TaskTextDto> tasks) {
        return tasks.stream().map(task -> map(task, TaskText.class)).toList();
    }

    public record TemplateDto(
            @NotBlank @Pattern(regexp = JIRA_KEY, flags = CASE_INSENSITIVE, message = JIRA_KEY_MESSAGE)
            String jiraProjectKey,
            @NotBlank @Size(max = 200) String assignmentGroup,
            @NotBlank @Size(max = 100) String category,
            @NotNull ChangeTemplate.Type type,
            @NotBlank @Size(max = 200) String configurationItem,
            @Size(max = 100) String release,
            @Size(max = 40) String incident,
            @Size(max = 40) String problem,
            @Size(max = 2000) String affectedClients,
            @Size(max = 2000) String description,
            @NotNull @Valid ApproversDto approvers,
            @NotNull Boolean downtime,
            @NotNull @Valid TimingDto timing,
            @NotNull @Valid PlanningDto planning,
            @NotNull @Valid PrivilegedAccessDto privilegedAccess,
            @NotNull @Valid RiskAssessmentDto riskAssessment) implements Mirrors<ChangeTemplate> {
    }

    public record ApproversDto(@Size(max = 200) String l1Manager, @Size(max = 200) String l2Manager,
                               @Size(max = 200) String businessApprover) implements Mirrors<Approvers> {
    }

    public record TimingDto(
            @NotBlank @Pattern(regexp = TIME_OF_DAY, message = TIME_OF_DAY_MESSAGE) String installationStart,
            @NotNull @Min(1) @Max(MAX_HOURS) Integer installationHours,
            @NotNull @Min(0) @Max(MAX_HOURS) Integer validationHours) implements Mirrors<Timing> {
    }

    public record PlanningDto(@NotBlank @Size(max = 2000) String testSummary,
                              @NotBlank @Size(max = 2000) String implementationPlan,
                              @NotBlank @Size(max = 2000) String validationPlan,
                              @NotBlank @Size(max = 2000) String backoutPlan,
                              @NotBlank @Size(max = 2000) String firstUsePlan) implements Mirrors<Planning> {
    }

    public record PrivilegedAccessDto(@NotNull Boolean required,
                                      @Size(max = MAX_PRIVILEGED_USERS) List<@NotNull @Valid PrivilegedUserDto> users)
            implements Mirrors<PrivilegedAccess> {
    }

    public record PrivilegedUserDto(@NotBlank @Size(max = 200) String user, @NotBlank @Size(max = 200) String account)
            implements Mirrors<PrivilegedUser> {
    }

    public record TaskTextDto(@NotBlank @Size(max = SHORT_DESCRIPTION_MAX) String shortDescription,
                              @NotBlank @Size(max = DESCRIPTION_MAX) String description)
            implements Mirrors<TaskText> {
    }

    public record RiskAssessmentDto(
            @PositiveOrZero Integer bbhWorkgroups,
            @PositiveOrZero Integer bbhUsers,
            @PositiveOrZero Integer bbhApplications,
            @PositiveOrZero Integer clients,
            @PositiveOrZero Integer clientsOutsideBbh,
            @Size(max = 100) String businessImpact,
            @Size(max = 100) String changeComplexity,
            @Size(max = 100) String validationComplexity,
            @Size(max = 2000) String backoutTesting,
            @Size(max = 100) String platformStatus) implements Mirrors<RiskAssessment> {
    }
}
