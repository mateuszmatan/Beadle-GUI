package com.bbh.itss.dso.portal.application.change.port.out;

import com.bbh.itss.dso.portal.domain.change.ChangeProfile;

import java.util.Optional;

public interface ChangeProfileRepositoryPort {

    Optional<ChangeProfile> find(long productId);

    ChangeProfile save(ChangeProfile profile);
}
