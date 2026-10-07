package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.change.port.out.ChangeProfileRepositoryPort;
import com.bbh.itss.dso.portal.domain.change.ChangeProfile;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.bbh.itss.dso.portal.domain.shared.Failures.staleVersion;

@Component
class ChangeProfilePersistenceAdapter implements ChangeProfileRepositoryPort {

    private final ChangeProfileJpaRepository profiles;

    ChangeProfilePersistenceAdapter(ChangeProfileJpaRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    public Optional<ChangeProfile> find(long productId) {
        return profiles.findByProductId(productId).map(ChangeProfileEntity::toDomain);
    }

    @Override
    public ChangeProfile save(ChangeProfile profile) {
        ChangeProfileEntity entity = profiles.findByProductId(profile.productId()).map(stored -> {
            if (stored.getVersion() != profile.version()) {
                throw staleVersion();
            }
            return stored;
        }).orElseGet(() -> new ChangeProfileEntity(profile.productId()));
        entity.apply(profile.template());
        return profiles.saveAndFlush(entity).toDomain();
    }
}
