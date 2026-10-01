package com.kitapsepeti.user.exception;

import com.kitapsepeti.common.error.ApiException;
import com.kitapsepeti.common.error.CommonErrorCode;

/**
 * İmzası geçerli bir token'ın ait olduğu kullanıcı artık yok (401). Token hâlâ süresi içinde olsa da
 * kimlik doğrulanmış sayılmaz; istemci yeniden giriş yapmalıdır.
 */
public class UnauthorizedException extends ApiException {

	public UnauthorizedException() {
		super(CommonErrorCode.UNAUTHORIZED);
	}

}
