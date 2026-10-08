package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedUser;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.EmbeddedColumnNaming;

import java.util.List;

import static com.bbh.itss.dso.portal.adapter.RecordMapper.map;
import static jakarta.persistence.EnumType.STRING;

@Embeddable
public record ChangeTemplateEmbeddable(
        String jiraProjectKey,
        String requestedFor,
        String requestedBy,
        @Column(name = "REQUEST_DEPARTMENT") String department,
        String assignmentGroup,
        String category,
        String assignedTo,
        @Enumerated(STRING) @Column(name = "CHANGE_TYPE") ChangeTemplate.Type type,
        @Column(name = "RELEASE_NAME") String release,
        String configurationItem,
        @Column(name = "INCIDENT_NUMBER") String incident,
        String directBusinessService,
        @Column(name = "PROBLEM_NUMBER") String problem,
        String affectedClients,
        String usersAffected,
        String description,
        ApproversEmbeddable approvers,
        Boolean downtime,
        @EmbeddedColumnNaming("TIMING_%s") TimingEmbeddable timing,
        PlanningEmbeddable planning,
        PrivilegedAccessEmbeddable privilegedAccess,
        @EmbeddedColumnNaming("RISK_%s") RiskAssessmentEmbeddable riskAssessment,
        String secureCodingTicket) {

    static ChangeTemplateEmbeddable of(ChangeTemplate template) {
        return map(template, ChangeTemplateEmbeddable.class);
    }

    static List<PrivilegedUserEmbeddable> usersOf(ChangeTemplate template) {
        return template.privilegedAccess().users().stream()
                .map(user -> map(user, PrivilegedUserEmbeddable.class)).toList();
    }

    ChangeTemplate toDomain(List<PrivilegedUserEmbeddable> users) {
        return map(ChangeTemplate.class, new Stored(new PrivilegedAccess(privilegedAccess.required(),
                users.stream().map(user -> map(user, PrivilegedUser.class)).toList())), this);
    }

    private record Stored(PrivilegedAccess privilegedAccess) {
    }

    @Embeddable
    public record ApproversEmbeddable(@Column(name = "L1_MANAGER") String l1Manager,
                                      @Column(name = "L2_MANAGER") String l2Manager, String businessApprover) {
    }

    @Embeddable
    public record TimingEmbeddable(String installationStart, Integer installationHours, Integer validationHours) {
    }

    @Embeddable
    public record PlanningEmbeddable(String testSummary, String implementationPlan, String validationPlan,
                                     String backoutPlan, String firstUsePlan) {
    }

    @Embeddable
    public record PrivilegedAccessEmbeddable(@Column(name = "PRIVILEGED_ACCESS_REQUIRED") Boolean required) {
    }

    @Embeddable
    public record PrivilegedUserEmbeddable(@Column(name = "USER_NAME") String user,
                                           @Column(name = "ACCOUNT_NAME") String account) {
    }

    @Embeddable
    public record RiskAssessmentEmbeddable(String bbhWorkgroups, String changeComplexity, String bbhUsers,
                                           String validationComplexity, String bbhApplications,
                                           String backoutTesting, String clientsOutsideBbh, String platformStatus,
                                           String businessImpact) {
    }
}
