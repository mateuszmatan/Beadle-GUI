package com.bbh.itss.dso.portal.application.change.port.in;

import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;

public interface ChangeProfilesUseCase {

    ChangeProfileView get(long productId);

    ChangeProfileView save(long productId, Long version, ChangeTemplate template);
}
