package com.bbh.itss.beadle.application.change.port.out;

import com.bbh.itss.beadle.domain.change.Lookup;
import com.bbh.itss.beadle.domain.change.LookupKind;

import java.util.List;

public interface ProTechLookupPort {

    List<Lookup> find(LookupKind kind, String query, int limit);
}
