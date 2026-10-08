package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.Mirrors;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Planning;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedUser;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing;
import com.bbh.itss.dso.portal.domain.change.RiskAssessment;
import com.bbh.itss.dso.portal.domain.change.TaskText;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.bbh.itss.dso.portal.adapter.RecordMapper.map;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.GROUP_MAX;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.JIRA_KEY;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.JIRA_KEY_MESSAGE;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.MAX_HOURS;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.MAX_PRIVILEGED_USERS;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.NAME_MAX;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.NUMBER_MAX;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.TEXT_MAX;
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
            @Size(max = GROUP_MAX) String requestedFor,
            @Size(max = GROUP_MAX) String requestedBy,
            @Size(max = NAME_MAX) String department,
            @NotBlank @Size(max = GROUP_MAX) String assignmentGroup,
            @NotBlank String category,
            @Size(max = GROUP_MAX) String assignedTo,
            @NotNull ChangeTemplate.Type type,
            @Size(max = NAME_MAX) String release,
            @NotBlank @Size(max = GROUP_MAX) String configurationItem,
            @Size(max = NUMBER_MAX) String incident,
            @Size(max = GROUP_MAX) String directBusinessService,
            @Size(max = NUMBER_MAX) String problem,
            @Size(max = TEXT_MAX) String affectedClients,
            @Size(max = TEXT_MAX) String usersAffected,
            @Size(max = TEXT_MAX) String description,
            @NotNull @Valid ApproversDto approvers,
            @NotNull Boolean downtime,
            @NotNull @Valid TimingDto timing,
            @NotNull @Valid PlanningDto planning,
            @NotNull @Valid PrivilegedAccessDto privilegedAccess,
            @NotNull RiskAssessmentDto riskAssessment,
            @Size(max = NUMBER_MAX) String secureCodingTicket) implements Mirrors<ChangeTemplate> {
    }

    public record ApproversDto(@Size(max = GROUP_MAX) String l1Manager, @Size(max = GROUP_MAX) String l2Manager,
                               @Size(max = GROUP_MAX) String businessApprover) implements Mirrors<Approvers> {
    }

    public record TimingDto(
            @NotBlank @Pattern(regexp = TIME_OF_DAY, message = TIME_OF_DAY_MESSAGE) String installationStart,
            @NotNull @Min(1) @Max(MAX_HOURS) Integer installationHours,
            @NotNull @Min(0) @Max(MAX_HOURS) Integer validationHours) implements Mirrors<Timing> {
    }

    public record PlanningDto(@NotBlank @Size(max = TEXT_MAX) String testSummary,
                              @NotBlank @Size(max = TEXT_MAX) String implementationPlan,
                              @NotBlank @Size(max = TEXT_MAX) String validationPlan,
                              @NotBlank @Size(max = TEXT_MAX) String backoutPlan,
                              @NotBlank @Size(max = TEXT_MAX) String firstUsePlan) implements Mirrors<Planning> {
    }

    public record PrivilegedAccessDto(@NotNull Boolean required,
                                      @Size(max = MAX_PRIVILEGED_USERS) List<@NotNull @Valid PrivilegedUserDto> users)
            implements Mirrors<PrivilegedAccess> {
    }

    public record PrivilegedUserDto(@NotBlank @Size(max = GROUP_MAX) String user,
                                    @NotBlank @Size(max = GROUP_MAX) String account)
            implements Mirrors<PrivilegedUser> {
    }

    public record TaskTextDto(@NotBlank @Size(max = SHORT_DESCRIPTION_MAX) String shortDescription,
                              @NotBlank @Size(max = DESCRIPTION_MAX) String description)
            implements Mirrors<TaskText> {
    }

    public record RiskAssessmentDto(String bbhWorkgroups, String changeComplexity, String bbhUsers,
                                    String validationComplexity, String bbhApplications, String backoutTesting,
                                    String clientsOutsideBbh, String platformStatus, String businessImpact)
            implements Mirrors<RiskAssessment> {
    }
}
