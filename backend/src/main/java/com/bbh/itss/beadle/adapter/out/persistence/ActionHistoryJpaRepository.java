package com.bbh.itss.beadle.adapter.out.persistence;

import com.bbh.itss.beadle.domain.audit.Action;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/** Insert and read only: no update or delete method, so nothing can change a row of the audit trail. */
public interface ActionHistoryJpaRepository extends Repository<ActionHistoryEntity, Long>,
        JpaSpecificationExecutor<ActionHistoryEntity> {

    ActionHistoryEntity save(ActionHistoryEntity entity);

    @Query("select distinct a.userName from ActionHistoryEntity a order by a.userName")
    List<String> findUserNames();

    @Query("select distinct a.action from ActionHistoryEntity a")
    List<Action> findActions();
}
