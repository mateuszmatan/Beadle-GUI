package com.bbh.itss.beadle.adapter.out.user;

import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("beadle")
public record SignedInUserProperties(@DefaultValue("Mateusz Matan") String signedInUser) implements SignedInUserPort {

    @Override
    public String name() {
        return signedInUser;
    }
}
