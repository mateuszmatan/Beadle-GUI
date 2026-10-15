package com.bbh.itss.beadle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where Beadle-Internal and Beadle-ProTech answer ({@code BEADLE_INTERNAL_URL}, {@code BEADLE_PROTECH_URL}) and the
 * token the three Beadle services share ({@code BEADLE_SERVICE_TOKEN}): Beadle-GUI sends it with every call and
 * requires it of every callback.
 */
@ConfigurationProperties("beadle.services")
public record ServicesProperties(String token, @DefaultValue Endpoint internal, @DefaultValue Endpoint protech) {

    public record Endpoint(String url) {
    }
}
