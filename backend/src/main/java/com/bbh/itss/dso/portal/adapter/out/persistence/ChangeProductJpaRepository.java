package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.change.ChangeProduct;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChangeProductJpaRepository extends Repository<ProductEntity, Long> {

    String CHANGE_PRODUCTS = """
            select new com.bbh.itss.dso.portal.domain.change.ChangeProduct(p.id, p.code, p.name, p.description,
                p.ownerTeam, p.departmentId, d.name, c.template.jiraProjectKey)
            from ProductEntity p left join p.department d left join ChangeProfileEntity c on c.productId = p.id
            """;

    @Query(CHANGE_PRODUCTS + "where p.id = :id")
    Optional<ChangeProduct> findChangeProduct(@Param("id") long productId);

    @Query(CHANGE_PRODUCTS + "order by p.name")
    List<ChangeProduct> findChangeProducts();
}
