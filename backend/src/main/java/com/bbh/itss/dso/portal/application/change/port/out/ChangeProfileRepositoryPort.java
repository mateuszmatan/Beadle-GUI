package com.bbh.itss.dso.portal.application.change.port.out;

import com.bbh.itss.dso.portal.domain.change.ChangeProfile;
import com.bbh.itss.dso.portal.domain.change.ChangeProfileSummary;

import java.util.List;
import java.util.Optional;

public interface ChangeProfileRepositoryPort {

    List<ChangeProfileSummary> summaries();

    Optional<ChangeProfile> find(long productId);

    ChangeProfile save(ChangeProfile profile);
}
