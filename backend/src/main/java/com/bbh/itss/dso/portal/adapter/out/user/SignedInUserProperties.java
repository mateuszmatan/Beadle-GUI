package com.bbh.itss.dso.portal.adapter.out.user;

import com.bbh.itss.dso.portal.application.user.port.out.SignedInUserPort;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("dso")
public record SignedInUserProperties(@DefaultValue("Mateusz Matan") String signedInUser) implements SignedInUserPort {

    @Override
    public String name() {
        return signedInUser;
    }
}
