package com.bbh.itss.beadle.adapter.out.persistence;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductionChangeJpaRepository extends JpaRepository<ProductionChangeEntity, Long> {

    @Override
    @EntityGraph(attributePaths = "tasks")
    List<ProductionChangeEntity> findAll(Sort sort);

    @EntityGraph(attributePaths = "tasks")
    List<ProductionChangeEntity> findByBusinessServiceIdOrderByIdDesc(long businessServiceId);

    @EntityGraph(attributePaths = "tasks")
    Optional<ProductionChangeEntity> findByNumber(String number);

    @Query("select c.businessServiceId, count(c) from ProductionChangeEntity c where c.businessServiceId is not null"
            + " group by c.businessServiceId")
    List<Object[]> countByBusinessService();

    @Modifying
    @Query("update ProductionChangeEntity c set c.syncedAt = :syncedAt where c.id in :ids")
    int recordSync(@Param("ids") Collection<Long> ids, @Param("syncedAt") Instant syncedAt);
}
