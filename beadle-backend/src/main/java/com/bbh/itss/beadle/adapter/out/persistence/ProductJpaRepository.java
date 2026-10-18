package com.bbh.itss.beadle.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByCodeIgnoreCase(String code);

    Optional<ProductEntity> findByNameIgnoreCase(String name);

    @Query("select p.businessServiceId, count(p) from ProductEntity p where p.businessServiceId is not null group by p.businessServiceId")
    List<Object[]> countByDepartment();
}
