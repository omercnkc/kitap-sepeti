package com.kitapsepeti.common.error;

import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;

/**
 * DB kısıt ihlallerinin servisten bağımsız okunması. Exception mesajı kullanıcı verisi içerebildiği için
 * (örn. çakışan e-posta/slug) mesaj yerine yalnızca Hibernate'in çıkardığı kısıt adı, türü ve MySQL hata kodu
 * kullanılır. Kısıt adı → {@link ErrorCode} eşlemesi servistedir.
 */
public final class DbConstraints {

	/** ER_ROW_IS_REFERENCED_2: başka satırların başvurduğu üst kayıt silinemez/değiştirilemez. */
	public static final int MYSQL_ROW_IS_REFERENCED = 1451;

	private DbConstraints() {
	}

	/** Hata zincirindeki ilk Hibernate {@link ConstraintViolationException}; yoksa null. */
	public static ConstraintViolationException find(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return violation;
			}
		}
		return null;
	}

	/** Zincirdeki kısıtın Hibernate'in verdiği haliyle adı (tablo öneki dahil olabilir); bulunamazsa null. */
	public static String nameOf(Throwable ex) {
		ConstraintViolationException violation = find(ex);
		return (violation != null) ? violation.getConstraintName() : null;
	}

	/** Hata zincirinde adı verilen kısıtın ihlali var mı (ad büyük/küçük harf ve tablo önekinden bağımsız). */
	public static boolean isViolated(Throwable ex, String constraintName) {
		ConstraintViolationException violation = find(ex);
		return violation != null && constraintName.equalsIgnoreCase(normalize(violation.getConstraintName()));
	}

	/** Yabancı anahtar ihlali, başvurulan üst kaydın silinmesi/değiştirilmesinden mi (MySQL 1451). */
	public static boolean isRowReferenced(ConstraintViolationException violation) {
		return violation.getKind() == ConstraintKind.FOREIGN_KEY
				&& violation.getErrorCode() == MYSQL_ROW_IS_REFERENCED;
	}

	/** MySQL UNIQUE ihlalinde adı "tablo.kısıt" biçiminde verir; tablo öneki atılır, küçük harfe çevrilir. */
	public static String normalize(String name) {
		if (name == null) {
			return null;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.substring(lower.lastIndexOf('.') + 1);
	}

}
