package com.kitapsepeti.catalog.mapper;

import com.kitapsepeti.catalog.dto.response.PublisherResponse;
import com.kitapsepeti.catalog.entity.Publisher;

public final class PublisherMapper {

	private PublisherMapper() {
	}

	public static PublisherResponse toResponse(Publisher publisher) {
		return new PublisherResponse(publisher.getId(), publisher.getName(), publisher.getSlug(),
				publisher.getCreatedAt(), publisher.getUpdatedAt());
	}

}
