package com.bbh.itss.dso.portal.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    List<ProductEntity> findAllByOrderByNameAsc();

    List<ProductEntity> findByDepartmentIdOrderByNameAsc(Long departmentId);

    Optional<ProductEntity> findByCodeIgnoreCase(String code);

    Optional<ProductEntity> findByNameIgnoreCase(String name);
}
