package com.bbh.itss.beadle.adapter.out.persistence;

import com.bbh.itss.beadle.application.change.port.out.ChangeProfileRepositoryPort;
import com.bbh.itss.beadle.domain.change.ChangeProfile;
import com.bbh.itss.beadle.domain.change.ChangeProfileSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.bbh.itss.beadle.adapter.out.persistence.AuditedEntity.current;

@Component
@RequiredArgsConstructor
class ChangeProfilePersistenceAdapter implements ChangeProfileRepositoryPort {

    private final ChangeProfileJpaRepository profiles;

    @Override
    public List<ChangeProfileSummary> summaries() {
        return profiles.summaries();
    }

    @Override
    public Optional<ChangeProfile> find(long productId) {
        return profiles.findByProductId(productId).map(ChangeProfileEntity::toDomain);
    }

    @Override
    public ChangeProfile save(ChangeProfile profile) {
        ChangeProfileEntity entity = profiles.findByProductId(profile.productId())
                .map(stored -> current(Optional.of(stored), profile.version()))
                .orElseGet(() -> new ChangeProfileEntity(profile.productId()));
        entity.apply(profile);
        return profiles.saveAndFlush(entity).toDomain();
    }
}
