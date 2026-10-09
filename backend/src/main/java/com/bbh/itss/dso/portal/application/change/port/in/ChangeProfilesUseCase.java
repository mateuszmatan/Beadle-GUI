package com.bbh.itss.dso.portal.application.change.port.in;

import com.bbh.itss.dso.portal.domain.change.ChangeProfileSummary;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import com.bbh.itss.dso.portal.domain.change.TaskDetails;

import java.util.List;

public interface ChangeProfilesUseCase {

    List<ChangeProfileSummary> list();

    ChangeProfileView get(long productId);

    ChangeProfileView save(long productId, Long version, ChangeTemplate template, List<TaskDetails> tasks);
}
