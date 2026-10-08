package com.bbh.itss.dso.portal.application.change.port.out;

import com.bbh.itss.dso.portal.domain.change.ProductionChange;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductionChangeRepositoryPort {

    List<ProductionChange> findAll();

    List<ProductionChange> findByDepartment(long departmentId);

    Optional<ProductionChange> load(long id);

    ProductionChange save(ProductionChange change);

    void synced(Collection<Long> ids, Instant syncedAt);
}
