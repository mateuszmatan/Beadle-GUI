package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.List;

@Embeddable
public class ChangeTemplateEmbeddable {

    private String jiraProjectKey;
    private String configurationItem;
    private String assignmentGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "CHANGE_TYPE")
    private ChangeTemplate.Type type;

    private String category;

    @Enumerated(EnumType.STRING)
    private ChangeTemplate.Risk risk;

    @Enumerated(EnumType.STRING)
    private ChangeTemplate.Impact impact;

    private String riskAssessment;
    private List<String> approvers;
    private String description;
    private String implementationPlan;
    private String backoutPlan;
    private String testPlan;

    protected ChangeTemplateEmbeddable() {
    }

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
