package com.bbh.itss.dso.portal.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DepartmentJpaRepository extends JpaRepository<DepartmentEntity, Long> {

    Optional<DepartmentEntity> findByNameIgnoreCase(String name);
}
