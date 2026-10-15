package com.bbh.itss.dso.portal.application.catalog.port.out;

import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.ProductDirectory;

import java.util.List;
import java.util.Optional;

public interface ProductRepositoryPort extends ProductDirectory {

    List<Product> findAll();

    Optional<Product> load(long id);

    Product save(Product product);

    void delete(long id);
}
