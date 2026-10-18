package com.bbh.itss.beadle.application.user;

import com.bbh.itss.beadle.application.Audited;
import com.bbh.itss.beadle.application.UseCase;
import com.bbh.itss.beadle.application.WithoutTransaction;
import com.bbh.itss.beadle.application.user.port.in.SignedInUser;
import com.bbh.itss.beadle.application.user.port.in.SignedInUserUseCase;
import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort;
import lombok.RequiredArgsConstructor;

import static com.bbh.itss.beadle.domain.audit.Action.USER_SIGNED_IN;
import static com.bbh.itss.beadle.domain.audit.Action.USER_SIGNED_OUT;

@UseCase
@RequiredArgsConstructor
public class SignedInUserService implements SignedInUserUseCase {

    private final SignedInUserPort user;

    @Override
    @WithoutTransaction
    public SignedInUser signedInUser() {
        return new SignedInUser(user.name(), user.email().orElse(null));
    }

    @Override
    @WithoutTransaction
    @Audited(USER_SIGNED_IN)
    public SignedInUser signIn() {
        return signedInUser();
    }

    @Override
    @WithoutTransaction
    @Audited(USER_SIGNED_OUT)
    public SignedInUser signOut() {
        return signedInUser();
    }
}
