package com.bbh.itss.beadle.application.catalog.port.in;

import java.util.List;

public interface BusinessServicesUseCase {

    List<BusinessServiceView> list();

    BusinessServiceView create(String name);

    BusinessServiceView rename(long id, Long version, String name);

    void delete(long id);
}
