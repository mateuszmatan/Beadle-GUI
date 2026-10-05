package com.bbh.itss.dso.portal.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GlobalSettingsJpaRepository extends JpaRepository<GlobalSettingsEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GlobalSettingsEntity s where s.id = :id")
    Optional<GlobalSettingsEntity> findForUpdate(@Param("id") Long id);
}
