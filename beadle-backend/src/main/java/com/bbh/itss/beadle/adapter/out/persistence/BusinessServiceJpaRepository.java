package com.bbh.itss.beadle.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface BusinessServiceJpaRepository extends JpaRepository<BusinessServiceEntity, Long> {

    Optional<BusinessServiceEntity> findByNameIgnoreCase(String name);
}
