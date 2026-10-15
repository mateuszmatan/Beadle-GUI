package com.bbh.itss.beadle.application.change.port.in;

import com.bbh.itss.beadle.domain.change.ChangeProfileSummary;
import com.bbh.itss.beadle.domain.change.ChangeTemplate;
import com.bbh.itss.beadle.domain.change.TaskDetails;

import java.util.List;

public interface ChangeProfilesUseCase {

    List<ChangeProfileSummary> list();

    ChangeProfileView get(long productId);

    ChangeProfileView save(long productId, Long version, ChangeTemplate template, List<TaskDetails> tasks);
}
