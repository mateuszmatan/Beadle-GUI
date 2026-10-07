package com.bbh.itss.dso.portal.adapter.out.persistence;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductionChangeJpaRepository extends JpaRepository<ProductionChangeEntity, Long> {

    @Override
    @EntityGraph(attributePaths = "tasks")
    List<ProductionChangeEntity> findAll(Sort sort);
}
