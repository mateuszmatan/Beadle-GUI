package com.bbh.itss.beadle.adapter.out.persistence;

import com.bbh.itss.beadle.application.catalog.port.out.BusinessServiceRepositoryPort;
import com.bbh.itss.beadle.domain.catalog.BusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.bbh.itss.beadle.adapter.out.persistence.AuditedEntity.current;

@Component
@RequiredArgsConstructor
public class BusinessServicePersistenceAdapter implements BusinessServiceRepositoryPort {

    private final BusinessServiceJpaRepository businessServices;

    @Override
    public List<BusinessService> findAll() {
        return businessServices.findAll().stream().map(BusinessServiceEntity::toDomain).toList();
    }

    @Override
    public Optional<BusinessService> load(long id) {
        return businessServices.findById(id).map(BusinessServiceEntity::toDomain);
    }

    @Override
    public Optional<BusinessService> findByName(String name) {
        return businessServices.findByNameIgnoreCase(name).map(BusinessServiceEntity::toDomain);
    }

    @Override
    public BusinessService save(BusinessService businessService) {
        BusinessServiceEntity entity = businessService.id() == null ? new BusinessServiceEntity()
                : current(businessServices.findById(businessService.id()), businessService.version());
        entity.rename(businessService.name());
        return businessServices.saveAndFlush(entity).toDomain();
    }

    @Override
    public void delete(long id) {
        businessServices.findById(id).ifPresent(businessServices::delete);
    }
}
