package com.kitapsepeti.order.client;

import java.util.List;
import java.util.UUID;

/** Bir uzak çağrının sınıflandırılmış ham sonucu ({@link RemoteCalls}); gateway bunu işleme özgü sonuca çevirir. */
public sealed interface CallOutcome<T> {

	/** 2xx ve gövde doğrulandı. */
	record Success<T>(int status, T body) implements CallOutcome<T> {

		@Override
		public String toString() {
			return "Success[status=" + this.status + "]";
		}

	}

	/** 4xx: karşı servis isteği işledi ve reddetti (yan etki yok). */
	record Problem<T>(int status, String code, List<UUID> bookIds) implements CallOutcome<T> {

		public Problem {
			bookIds = List.copyOf(bookIds);
		}

		@Override
		public String toString() {
			return "Problem[status=" + this.status + ", code=" + this.code + "]";
		}

	}

	/** İstek karşıya ulaşmadı: bağlantı kurulamadı ya da circuit breaker açık. */
	record NotSent<T>() implements CallOutcome<T> {
	}

	/** İstek gönderildi, sonuç bilinmiyor: okuma zaman aşımı, kopma, 5xx/3xx, sözleşmeye uymayan 2xx. */
	record Failed<T>() implements CallOutcome<T> {
	}

}
