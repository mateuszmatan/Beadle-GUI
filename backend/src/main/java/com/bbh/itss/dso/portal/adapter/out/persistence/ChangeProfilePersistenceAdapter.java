package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.change.port.out.ChangeProfileRepositoryPort;
import com.bbh.itss.dso.portal.domain.change.ChangeProfile;
import com.bbh.itss.dso.portal.domain.change.ChangeProfileSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.bbh.itss.dso.portal.domain.shared.Failures.staleVersion;

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
        ChangeProfileEntity entity = profiles.findByProductId(profile.productId()).map(stored -> {
            if (stored.version() != profile.version()) {
                throw staleVersion();
            }
            return stored;
        }).orElseGet(() -> new ChangeProfileEntity(profile.productId()));
        entity.apply(profile);
        return profiles.saveAndFlush(entity).toDomain();
    }
}
