package com.kitapsepeti.user.exception;

import com.kitapsepeti.common.error.ApiException;

/** Parola doğru ama hesap askıya alınmış (403). Yalnızca doğru parolayla girişte fırlatılır. */
public class AccountSuspendedException extends ApiException {

	public AccountSuspendedException() {
		super(UserErrorCode.ACCOUNT_SUSPENDED);
	}

}
