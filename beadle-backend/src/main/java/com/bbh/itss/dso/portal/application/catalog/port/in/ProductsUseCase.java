package com.bbh.itss.dso.portal.application.catalog.port.in;

import java.util.List;

public interface ProductsUseCase {

    List<ProductView> list(String search);

    ProductView get(long id);

    ProductView create(ProductCommand command);

    ProductView update(long id, ProductCommand command);

    void delete(long id);

    String suggestCode(String name);
}
