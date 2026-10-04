package com.kitapsepeti.payment.exception;

import java.util.Map;

import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.DbConstraints;
import com.kitapsepeti.common.error.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;

/**
 * Ödeme DB kısıt ihlallerini {@link ErrorCode}'a çevirir (tek eşleme noktası). Kısıt adı/türü {@link DbConstraints}
 * ile okunur; mesaj kullanılmaz. {@code uk_payments_order} yarışı serviste bir kez yeniden denenerek çözülür; buraya
 * ulaşan ihlal beklenmeyen bir yarış ya da hatadır ve istemciye CONFLICT olarak döner.
 */
public final class DbConstraintCodes {

	/** Bilinen UNIQUE kısıtları; özel kod gerekirse yalnızca bu eşleme değişir. */
	private static final Map<String, ErrorCode> UNIQUE_CODES = Map.of(
			"uk_payments_order", CommonErrorCode.CONFLICT,
			"uk_payments_provider_ref", CommonErrorCode.CONFLICT,
			"uk_provider_events_provider_event", CommonErrorCode.CONFLICT);

	private DbConstraintCodes() {
	}

	/**
	 * Eşleme sonucu. {@code constraint} ve {@code kind} Hibernate istisnası bulunamazsa null'dır.
	 * {@link #logNote()} yalnızca kısıt adı ve türünü içerir; değer içermez.
	 */
	public record Violation(ErrorCode code, String constraint, ConstraintKind kind) {

		public String logNote() {
			return (constraint == null) ? null : "constraint=" + constraint + ", kind=" + kind;
		}

	}

	/** UNIQUE: {@link #UNIQUE_CODES}, bilinmeyen ad CONFLICT. FOREIGN_KEY, CHECK ve geri kalan her şey CONFLICT. */
	public static Violation classify(Throwable ex) {
		ConstraintViolationException violation = DbConstraints.find(ex);
		if (violation == null) {
			return new Violation(CommonErrorCode.CONFLICT, null, null);
		}
		String constraint = DbConstraints.normalize(violation.getConstraintName());
		ConstraintKind kind = violation.getKind();
		ErrorCode code = (kind == ConstraintKind.UNIQUE && constraint != null)
				? UNIQUE_CODES.getOrDefault(constraint, CommonErrorCode.CONFLICT)
				: CommonErrorCode.CONFLICT;
		return new Violation(code, constraint, kind);
	}

}
