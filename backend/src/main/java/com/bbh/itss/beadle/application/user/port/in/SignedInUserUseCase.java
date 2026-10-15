package com.bbh.itss.beadle.application.user.port.in;

public interface SignedInUserUseCase {

    SignedInUser signedInUser();

    /** Records that the signed-in user started a session; called once per session. */
    SignedInUser signIn();
}
