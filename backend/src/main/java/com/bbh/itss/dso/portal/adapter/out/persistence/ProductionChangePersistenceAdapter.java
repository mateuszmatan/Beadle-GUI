package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.change.port.out.ProductionChangeRepositoryPort;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.bbh.itss.dso.portal.adapter.out.persistence.AuditedEntity.current;
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
    public List<ProductionChange> findByDepartment(long departmentId) {
        return changes.findByDepartmentIdOrderByIdDesc(departmentId).stream()
                .map(ProductionChangeEntity::toDomain).toList();
    }

    @Override
    public Optional<ProductionChange> load(long id) {
        return changes.findById(id).map(ProductionChangeEntity::toDomain);
    }

    @Override
    public ProductionChange save(ProductionChange change) {
        if (change.id() == null) {
            return changes.saveAndFlush(new ProductionChangeEntity(change)).toDomain();
        }
        ProductionChangeEntity entity = current(changes.findById(change.id()), change.version()).apply(change);
        entity.touch();
        return changes.saveAndFlush(entity).toDomain();
    }

    @Override
    public void synced(long id, Instant syncedAt) {
        changes.recordSync(id, syncedAt);
    }
}
