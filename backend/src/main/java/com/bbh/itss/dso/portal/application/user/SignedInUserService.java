package com.bbh.itss.dso.portal.application.user;

import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.WithoutTransaction;
import com.bbh.itss.dso.portal.application.user.port.in.SignedInUser;
import com.bbh.itss.dso.portal.application.user.port.in.SignedInUserUseCase;
import com.bbh.itss.dso.portal.application.user.port.out.SignedInUserPort;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class SignedInUserService implements SignedInUserUseCase {

    private final SignedInUserPort user;

    @Override
    @WithoutTransaction
    public SignedInUser signedInUser() {
        return new SignedInUser(user.name());
    }
}
