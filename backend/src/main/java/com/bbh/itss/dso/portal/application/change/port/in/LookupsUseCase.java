package com.bbh.itss.dso.portal.application.change.port.in;

import com.bbh.itss.dso.portal.domain.change.Lookup;

import java.util.List;

public interface LookupsUseCase {

    List<Lookup> find(String kind, String query);
}
