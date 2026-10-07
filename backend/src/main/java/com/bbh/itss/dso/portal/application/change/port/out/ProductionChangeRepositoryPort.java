package com.bbh.itss.dso.portal.application.change.port.out;

import com.bbh.itss.dso.portal.domain.change.ProductionChange;

import java.util.List;
import java.util.Optional;

public interface ProductionChangeRepositoryPort {

    List<ProductionChange> findAll();

    Optional<ProductionChange> load(long id);

    ProductionChange save(ProductionChange change);
}
