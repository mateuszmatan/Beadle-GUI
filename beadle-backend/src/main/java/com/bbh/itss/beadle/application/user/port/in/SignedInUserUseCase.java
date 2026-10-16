package com.bbh.itss.beadle.application.user.port.in;

public interface SignedInUserUseCase {

    SignedInUser signedInUser();

    /** Records that the signed-in user started a session: called on the first API call of every session. */
    SignedInUser signIn();
}
