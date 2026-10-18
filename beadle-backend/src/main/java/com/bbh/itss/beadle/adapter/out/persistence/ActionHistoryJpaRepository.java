package com.bbh.itss.beadle.adapter.out.persistence;

import com.bbh.itss.beadle.domain.audit.Action;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Insert and read only: the interface extends neither {@code CrudRepository} nor {@code JpaSpecificationExecutor}
 * (which brings {@code update} and {@code delete} by specification) and declares no update or delete method, so
 * nothing in Beadle can change or remove a row of the audit trail. {@link #findAll(Specification, Pageable)} is the
 * one method of {@code JpaSpecificationExecutor} it exposes; Spring Data runs it in {@code SimpleJpaRepository}.
 */
public interface ActionHistoryJpaRepository extends Repository<ActionHistoryEntity, Long> {

    ActionHistoryEntity save(ActionHistoryEntity entity);

    Page<ActionHistoryEntity> findAll(Specification<ActionHistoryEntity> specification, Pageable pageable);

    @Query("select distinct a.user from ActionHistoryEntity a order by a.user")
    List<String> findUserNames();

    @Query("select distinct a.action from ActionHistoryEntity a")
    List<Action> findActions();
}
