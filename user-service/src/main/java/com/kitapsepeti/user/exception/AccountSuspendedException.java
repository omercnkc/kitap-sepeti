package com.kitapsepeti.user.exception;

/** Parola doğru ama hesap askıya alınmış (403). Yalnızca doğru parolayla girişte fırlatılır. */
public class AccountSuspendedException extends ApiException {

	public AccountSuspendedException() {
		super(ErrorCode.ACCOUNT_SUSPENDED);
	}

}
