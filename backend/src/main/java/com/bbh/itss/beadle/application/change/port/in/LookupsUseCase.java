package com.bbh.itss.beadle.application.change.port.in;

import com.bbh.itss.beadle.domain.change.Lookup;

import java.util.List;

public interface LookupsUseCase {

    List<Lookup> find(String kind, String query);
}
