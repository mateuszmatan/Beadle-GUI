package com.bbh.itss.beadle.application.change;

import com.bbh.itss.beadle.application.UseCase;
import com.bbh.itss.beadle.application.WithoutTransaction;
import com.bbh.itss.beadle.application.change.port.in.LookupsUseCase;
import com.bbh.itss.beadle.application.change.port.out.ProTechLookupPort;
import com.bbh.itss.beadle.domain.change.Lookup;
import com.bbh.itss.beadle.domain.change.LookupKind;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.bbh.itss.beadle.domain.change.Lookup.MAX_LOOKUPS;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

@UseCase
@RequiredArgsConstructor
public class LookupService implements LookupsUseCase {

    private final ProTechLookupPort lookups;

    @Override
    @WithoutTransaction
    public List<Lookup> find(String kind, String query) {
        return lookups.find(LookupKind.of(kind), trimToEmpty(query), MAX_LOOKUPS);
    }
}
