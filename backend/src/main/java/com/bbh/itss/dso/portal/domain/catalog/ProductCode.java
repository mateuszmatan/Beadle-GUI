package com.bbh.itss.dso.portal.domain.catalog;

import java.text.Normalizer;
import java.util.Locale;

public final class ProductCode {

    private static final int MAX_LENGTH = 50;
    private static final String FALLBACK = "PRODUCT";

    private ProductCode() {
    }

    public static String suggest(String name, ProductDirectory directory) {
        String base = fromName(name);
        String code = base;
        for (int suffix = 2; directory.findProductByCode(code).isPresent(); suffix++) {
            String number = Integer.toString(suffix);
            code = base.substring(0, Math.min(base.length(), MAX_LENGTH - number.length())) + number;
        }
        return code;
    }

    static String fromName(String name) {
        String letters = Normalizer.normalize(name == null ? "" : name.replace('Ł', 'L').replace('ł', 'l'),
                        Normalizer.Form.NFD)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
        if (letters.length() < 2) {
            letters = FALLBACK + letters;
        } else if (!Character.isLetter(letters.charAt(0))) {
            letters = "P" + letters;
        }
        return letters.substring(0, Math.min(letters.length(), MAX_LENGTH));
    }
}
