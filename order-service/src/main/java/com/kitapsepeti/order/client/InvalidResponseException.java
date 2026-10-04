package com.kitapsepeti.order.client;

/** 2xx yanıt sözleşmeye uymuyor (boş gövde, başka sipariş, beklenmeyen durum...). Mesajda değer yok. */
public class InvalidResponseException extends RuntimeException {

	public InvalidResponseException(String message) {
		super(message, null, false, false);
	}

}
