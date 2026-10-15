package com.bbh.itss.beadle.application.catalog.port.out;

import com.bbh.itss.beadle.domain.catalog.Product;
import com.bbh.itss.beadle.domain.catalog.ProductDirectory;

import java.util.List;
import java.util.Optional;

public interface ProductRepositoryPort extends ProductDirectory {

    List<Product> findAll();

    Optional<Product> load(long id);

    Product save(Product product);

    void delete(long id);
}
