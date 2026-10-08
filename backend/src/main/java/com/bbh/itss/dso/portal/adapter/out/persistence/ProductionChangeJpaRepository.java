package com.bbh.itss.dso.portal.adapter.out.persistence;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ProductionChangeJpaRepository extends JpaRepository<ProductionChangeEntity, Long> {

    @Override
    @EntityGraph(attributePaths = "tasks")
    List<ProductionChangeEntity> findAll(Sort sort);

    @EntityGraph(attributePaths = "tasks")
    List<ProductionChangeEntity> findByDepartmentIdOrderByIdDesc(long departmentId);

    @Query("select c.departmentId, count(c) from ProductionChangeEntity c where c.departmentId is not null"
            + " group by c.departmentId")
    List<Object[]> countByDepartment();

    @Modifying
    @Query("update ProductionChangeEntity c set c.syncedAt = :syncedAt where c.id in :ids")
    int recordSync(@Param("ids") Collection<Long> ids, @Param("syncedAt") Instant syncedAt);
}
