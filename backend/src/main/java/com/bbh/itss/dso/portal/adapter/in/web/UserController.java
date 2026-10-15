package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.user.port.in.SignedInUser;
import com.bbh.itss.dso.portal.application.user.port.in.SignedInUserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {

    private final SignedInUserUseCase users;

    @GetMapping
    public SignedInUser me() {
        return users.signedInUser();
    }
}
