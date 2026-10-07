package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.change.port.out.ProductionChangeRepositoryPort;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static org.springframework.data.domain.Sort.Direction.DESC;

@Component
@RequiredArgsConstructor
class ProductionChangePersistenceAdapter implements ProductionChangeRepositoryPort {

    private final ProductionChangeJpaRepository changes;

    @Override
    public List<ProductionChange> findAll() {
        return changes.findAll(Sort.by(DESC, "id")).stream()
                .map(ProductionChangeEntity::toDomain).toList();
    }

    @Override
    public Optional<ProductionChange> load(long id) {
        return changes.findById(id).map(ProductionChangeEntity::toDomain);
    }

    @Override
    public ProductionChange save(ProductionChange change) {
        return changes.saveAndFlush(new ProductionChangeEntity(change)).toDomain();
    }
}
