package com.bbh.itss.beadle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** The user everybody is signed in as in the auth mode {@code local} ({@code BEADLE_SIGNED_IN_USER}). */
@ConfigurationProperties("beadle")
public record SignedInUserProperties(@DefaultValue("Mateusz Matan") String signedInUser) {
}
