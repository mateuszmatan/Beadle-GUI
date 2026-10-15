package com.bbh.itss.beadle.application.change.port.out;

import com.bbh.itss.beadle.domain.change.ChangeProfile;
import com.bbh.itss.beadle.domain.change.ChangeProfileSummary;

import java.util.List;
import java.util.Optional;

public interface ChangeProfileRepositoryPort {

    List<ChangeProfileSummary> summaries();

    Optional<ChangeProfile> find(long productId);

    ChangeProfile save(ChangeProfile profile);
}
