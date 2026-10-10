package com.bbh.itss.dso.portal.application.change.port.in;

import com.bbh.itss.dso.portal.domain.change.JiraIssue;
import com.bbh.itss.dso.portal.domain.change.JiraVersion;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;

import java.util.List;

public interface ProductionChangesUseCase {

    List<ProductionChange> list(Long departmentId);

    ProductionChange get(long id);

    ProductionChange update(long id, ChangeEditCommand command);

    ChangeIntegrations integrations();

    List<JiraVersion> versions(long productId, String project);

    List<JiraIssue> epics(long productId, String fixVersion, String project);

    List<JiraIssue> stories(long productId, String fixVersion, List<String> epicKeys, String project);

    ProductionChange preview(ChangeCommand command);

    ProductionChange raise(ChangeCommand command);

    ProductionChange createTasks(long id, ChangeTasksCommand command);

    ProductionChange createSecureCodingTicket(long id, SecureCodingCommand command);
}
