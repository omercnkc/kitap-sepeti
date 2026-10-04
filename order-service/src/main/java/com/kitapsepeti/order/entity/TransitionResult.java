package com.kitapsepeti.order.entity;

/**
 * {@link Order} geçiş metotlarının sonucu (payment ile aynı kalıp). Tekrarlanan ya da çelişen sonuç (ör. aynı ödeme
 * olayı iki kez, failed iken geç gelen başarı) programlama hatası değil, beklenen bir durumdur; bu yüzden exception
 * yerine sonuç döner. Geçişin hiç denenmemesi gereken durumlar (ör. rezervasyonsuz ödeme) {@link IllegalStateException}.
 */
public enum TransitionResult {

	/** Geçiş uygulandı; {@code updated_at} damgalandı (durum geçişiyse geçmiş satırı eklendi). */
	APPLIED,
	/** Zaten hedef durumda; hiçbir şey değişmedi. */
	ALREADY_IN_STATE,
	/** Hedefle çelişen bir durumda; hiçbir şey değişmedi. */
	CONFLICTING_FINAL

}
