package com.bbh.itss.beadle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How people sign in to Beadle: {@code oidc} with Ping, the registration {@code ping} of
 * {@code spring.security.oauth2.client}; or {@code local}, for profile {@code local} only, where everybody is
 * {@code user} ({@code BEADLE_SIGNED_IN_USER}).
 */
@ConfigurationProperties("beadle.auth")
public record AuthProperties(Mode mode, String user) {

    public enum Mode {
        LOCAL, OIDC
    }
}
