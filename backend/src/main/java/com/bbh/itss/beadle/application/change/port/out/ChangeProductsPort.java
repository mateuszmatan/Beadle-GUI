package com.bbh.itss.beadle.application.change.port.out;

import com.bbh.itss.beadle.domain.change.ChangeProduct;

import java.util.List;

public interface ChangeProductsPort {

    ChangeProduct get(long productId);

    List<ChangeProduct> findAll();
}
