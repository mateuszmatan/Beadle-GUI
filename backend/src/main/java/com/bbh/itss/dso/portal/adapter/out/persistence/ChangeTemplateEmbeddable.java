package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Enumerated;
import lombok.NoArgsConstructor;

import java.util.List;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

@Embeddable
@NoArgsConstructor(access = PROTECTED)
public class ChangeTemplateEmbeddable {

    private String jiraProjectKey;
    private String configurationItem;
    private String assignmentGroup;

    @Enumerated(STRING)
    @Column(name = "CHANGE_TYPE")
    private ChangeTemplate.Type type;

    private String category;

    @Enumerated(STRING)
    private ChangeTemplate.Risk risk;

    @Enumerated(STRING)
    private ChangeTemplate.Impact impact;

    private String riskAssessment;
    private List<String> approvers;
    private String description;
    private String implementationPlan;
    private String backoutPlan;
    private String testPlan;

    ChangeTemplateEmbeddable(ChangeTemplate template) {
        jiraProjectKey = template.jiraProjectKey();
        configurationItem = template.configurationItem();
        assignmentGroup = template.assignmentGroup();
        type = template.type();
        category = template.category();
        risk = template.risk();
        impact = template.impact();
        riskAssessment = template.riskAssessment();
        approvers = template.approvers();
        description = template.description();
        implementationPlan = template.implementationPlan();
        backoutPlan = template.backoutPlan();
        testPlan = template.testPlan();
    }
}
