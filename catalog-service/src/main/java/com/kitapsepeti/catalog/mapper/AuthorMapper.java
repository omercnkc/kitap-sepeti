package com.kitapsepeti.catalog.mapper;

import com.kitapsepeti.catalog.dto.response.AuthorResponse;
import com.kitapsepeti.catalog.entity.Author;

public final class AuthorMapper {

	private AuthorMapper() {
	}

	public static AuthorResponse toResponse(Author author) {
		return new AuthorResponse(author.getId(), author.getName(), author.getSlug(), author.getCreatedAt(),
				author.getUpdatedAt());
	}

}
