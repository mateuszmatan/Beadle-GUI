package com.bbh.itss.dso.portal.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChangeProfileJpaRepository extends JpaRepository<ChangeProfileEntity, Long> {

    Optional<ChangeProfileEntity> findByProductId(long productId);
}
