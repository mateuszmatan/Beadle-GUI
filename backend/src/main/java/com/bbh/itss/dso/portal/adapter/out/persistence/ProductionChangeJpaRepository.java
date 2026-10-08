package com.bbh.itss.dso.portal.adapter.out.persistence;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProductionChangeJpaRepository extends JpaRepository<ProductionChangeEntity, Long> {

    @Override
    @EntityGraph(attributePaths = "tasks")
    List<ProductionChangeEntity> findAll(Sort sort);

    @EntityGraph(attributePaths = "tasks")
    List<ProductionChangeEntity> findByDepartmentIdOrderByIdDesc(long departmentId);

    @Modifying
    @Query("update ProductionChangeEntity c set c.syncedAt = :syncedAt where c.id = :id")
    int recordSync(@Param("id") long id, @Param("syncedAt") Instant syncedAt);
}
