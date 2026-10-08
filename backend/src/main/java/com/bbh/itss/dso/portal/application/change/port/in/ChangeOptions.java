package com.bbh.itss.dso.portal.application.change.port.in;

import java.util.List;
import java.util.Map;

public record ChangeOptions(List<String> categories, List<TypeOption> types, Map<String, List<String>> risk) {

    public record TypeOption(String value, String label) {
    }
}
