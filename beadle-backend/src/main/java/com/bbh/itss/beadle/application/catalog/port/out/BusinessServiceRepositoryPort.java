package com.bbh.itss.beadle.application.catalog.port.out;

import com.bbh.itss.beadle.domain.catalog.BusinessService;

import java.util.List;
import java.util.Optional;

public interface BusinessServiceRepositoryPort {

    List<BusinessService> findAll();

    Optional<BusinessService> load(long id);

    Optional<BusinessService> findByName(String name);

    BusinessService save(BusinessService businessService);

    void delete(long id);
}
