package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.bbh.itss.dso.portal.adapter.out.persistence.AuditedEntity.current;

@Component
@RequiredArgsConstructor
class ProductPersistenceAdapter implements ProductRepositoryPort {

    private final ProductJpaRepository products;
    private final DepartmentJpaRepository departments;

    @Override
    public List<Product> findAll() {
        return products.findAll().stream().map(ProductEntity::toDomain).toList();
    }

    @Override
    public Optional<Product> load(long id) {
        return products.findById(id).map(ProductEntity::toDomain);
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = product.id() == null ? new ProductEntity()
                : current(products.findById(product.id()), product.version());
        entity.apply(product.details());
        return products.saveAndFlush(entity).toDomain();
    }

    @Override
    public void delete(long id) {
        products.findById(id).ifPresent(products::delete);
    }

    @Override
    public Optional<ProductIdentity> findProductByCode(String code) {
        return products.findByCodeIgnoreCase(code).map(ProductPersistenceAdapter::identity);
    }

    @Override
    public Optional<ProductIdentity> findProductByName(String name) {
        return products.findByNameIgnoreCase(name).map(ProductPersistenceAdapter::identity);
    }

    @Override
    public boolean departmentExists(long id) {
        return departments.existsById(id);
    }

    private static ProductIdentity identity(ProductEntity product) {
        return new ProductIdentity(product.id(), product.name());
    }
}
