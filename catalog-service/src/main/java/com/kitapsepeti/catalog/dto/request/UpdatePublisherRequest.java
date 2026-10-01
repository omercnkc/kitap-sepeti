package com.kitapsepeti.catalog.dto.request;

import com.kitapsepeti.catalog.validation.NullOrNotBlank;
import com.kitapsepeti.catalog.validation.Slug;
import jakarta.validation.constraints.Size;

/** Kısmi güncelleme: null alan değiştirilmez. Yalnızca ad değişirse slug korunur (mevcut adresler kırılmaz). */
public record UpdatePublisherRequest(@NullOrNotBlank @Size(max = 160) String name, @Slug(max = 160) String slug) {

	public UpdatePublisherRequest {
		name = (name != null) ? name.strip() : null;
	}

}
