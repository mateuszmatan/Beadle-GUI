package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.catalog.port.out.ChangeCountsPort;
import com.bbh.itss.dso.portal.application.change.port.out.ProductionChangeRepositoryPort;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.bbh.itss.dso.portal.adapter.out.persistence.AuditedEntity.current;
import static com.bbh.itss.dso.portal.adapter.out.persistence.Counts.perId;
import static com.bbh.itss.dso.portal.domain.shared.Failures.staleVersion;
import static org.springframework.data.domain.Sort.Direction.DESC;

@Component
@Transactional
@RequiredArgsConstructor
class ProductionChangePersistenceAdapter implements ProductionChangeRepositoryPort, ChangeCountsPort {

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
        ProductionChangeEntity entity = current(changes.findById(change.id()), change.version()).update(change);
        try {
            return changes.saveAndFlush(entity).toDomain();
        } catch (OptimisticLockingFailureException e) {
            throw staleVersion();
        }
    }

    @Override
    public void synced(Collection<Long> ids, Instant syncedAt) {
        changes.recordSync(ids, syncedAt);
    }

    @Override
    public Map<Long, Long> changesPerDepartment() {
        return perId(changes.countByDepartment());
    }
}
