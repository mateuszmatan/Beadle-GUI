package com.bbh.itss.beadle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Where Beadle-Internal and Beadle-ProTech answer ({@code BEADLE_INTERNAL_URL}, {@code BEADLE_PROTECH_URL}) and the
 * token the three Beadle services share ({@code BEADLE_SERVICE_TOKEN}): Beadle-GUI sends it with every call and
 * requires it of every callback. The token is trimmed, as Beadle-Internal and Beadle-ProTech trim theirs: a secret
 * made with {@code oc create secret --from-file} keeps the newline of its file, which no header value may carry.
 */
@ConfigurationProperties("beadle.services")
public record ServicesProperties(String token, @DefaultValue Endpoint internal, @DefaultValue Endpoint protech) {

    public ServicesProperties {
        token = trimToNull(token);
    }

    public record Endpoint(String url) {
    }
}
