package com.bbh.itss.beadle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How people sign in to Beadle ({@code BEADLE_AUTH_MODE}): {@code oidc} with Ping, the registration {@code ping} of
 * {@code spring.security.oauth2.client}; or {@code local}, where everybody is {@code beadle.signed-in-user}, for the
 * profile {@code local} and the tests.
 */
@ConfigurationProperties("beadle.auth")
public record AuthProperties(@DefaultValue("oidc") Mode mode) {

    public enum Mode {
        LOCAL, OIDC
    }
}
