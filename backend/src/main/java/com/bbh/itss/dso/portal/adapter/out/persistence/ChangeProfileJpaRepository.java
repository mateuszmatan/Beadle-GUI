package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileSummary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChangeProfileJpaRepository extends JpaRepository<ChangeProfileEntity, Long> {

    @EntityGraph(attributePaths = "privilegedUsers")
    Optional<ChangeProfileEntity> findByProductId(long productId);

    @Query("""
            select new com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileSummary(p.productId, pr.name,
                p.version, p.updatedAt)
            from ChangeProfileEntity p join ProductEntity pr on pr.id = p.productId
            order by pr.name, p.productId""")
    List<ChangeProfileSummary> summaries();
}
