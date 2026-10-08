package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.change.port.out.ChangeProductsPort;
import com.bbh.itss.dso.portal.domain.change.ChangeProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.bbh.itss.dso.portal.domain.shared.Failures.notFound;

@Component
@RequiredArgsConstructor
class ChangeProductPersistenceAdapter implements ChangeProductsPort {

    private final ChangeProductJpaRepository products;

    @Override
    public ChangeProduct get(long productId) {
        return products.findChangeProduct(productId).orElseThrow(() -> notFound("Product", productId));
    }

    @Override
    public List<ChangeProduct> findAll() {
        return products.findChangeProducts();
    }
}
