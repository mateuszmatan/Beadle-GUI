package com.bbh.itss.dso.portal.application.change.port.in;

import com.bbh.itss.dso.portal.domain.change.DateRange;
import com.bbh.itss.dso.portal.domain.change.JiraIssue;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;

import java.util.List;

public interface ProductionChangesUseCase {

    List<ProductionChange> list();

    ProductionChange get(long id);

    ChangeIntegrations integrations();

    List<JiraIssue> epics(long productId, DateRange updated);

    List<JiraIssue> stories(long productId, List<String> epicKeys, DateRange updated);

    ProductionChange preview(ChangeCommand command);

    ProductionChange raise(ChangeCommand command);
}
