package com.bbh.itss.dso.portal.domain.catalog;

import lombok.NoArgsConstructor;

import static java.lang.Character.isLetter;
import static java.text.Normalizer.Form.NFD;
import static java.text.Normalizer.normalize;
import static java.util.Locale.ROOT;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.truncate;

@NoArgsConstructor(access = PRIVATE)
public final class ProductCode {

    private static final int MAX_LENGTH = 50;
    private static final String FALLBACK = "PRODUCT";

    public static String suggest(String name, ProductDirectory directory) {
        String base = fromName(name);
        String code = base;
        for (int suffix = 2; directory.findProductByCode(code).isPresent(); suffix++) {
            String number = Integer.toString(suffix);
            code = truncate(base, MAX_LENGTH - number.length()) + number;
        }
        return code;
    }

    static String fromName(String name) {
        String letters = normalize(defaultString(name).replace('Ł', 'L').replace('ł', 'l'), NFD)
                .toUpperCase(ROOT)
                .replaceAll("[^A-Z0-9]", "");
        if (letters.length() < 2) {
            letters = FALLBACK + letters;
        } else if (!isLetter(letters.charAt(0))) {
            letters = "P" + letters;
        }
        return truncate(letters, MAX_LENGTH);
    }
}
