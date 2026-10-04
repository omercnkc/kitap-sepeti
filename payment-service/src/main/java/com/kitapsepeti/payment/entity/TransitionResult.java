package com.kitapsepeti.payment.entity;

/**
 * {@link Payment#succeed} / {@link Payment#fail} sonucu. Tekrarlanan ya da çelişen sonuç (ör. aynı webhook iki kez,
 * succeeded iken failed) programlama hatası değil, beklenen bir durumdur; bu yüzden exception yerine sonuç döner.
 */
public enum TransitionResult {

	/** {@code initiated}'dan son duruma geçti; {@code updated_at} damgalandı. */
	APPLIED,
	/** Zaten aynı son durumda (failed için aynı kodla); hiçbir şey değişmedi. */
	ALREADY_IN_STATE,
	/** Başka bir son durumda; hiçbir şey değişmedi. */
	CONFLICTING_FINAL

}
