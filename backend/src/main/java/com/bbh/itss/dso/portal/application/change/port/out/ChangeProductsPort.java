package com.bbh.itss.dso.portal.application.change.port.out;

import com.bbh.itss.dso.portal.domain.change.ChangeProduct;

import java.util.List;

public interface ChangeProductsPort {

    ChangeProduct get(long productId);

    List<ChangeProduct> findAll();
}
