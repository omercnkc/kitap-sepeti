package com.kitapsepeti.catalog.service;

import java.util.UUID;

import com.kitapsepeti.catalog.dto.request.AdminPageRequest;
import com.kitapsepeti.catalog.dto.request.CreateAuthorRequest;
import com.kitapsepeti.catalog.dto.request.UpdateAuthorRequest;
import com.kitapsepeti.catalog.dto.response.AuthorResponse;
import com.kitapsepeti.catalog.dto.response.PageResponse;
import com.kitapsepeti.catalog.entity.Author;
import com.kitapsepeti.catalog.exception.ResourceNotFoundException;
import com.kitapsepeti.catalog.exception.SlugAlreadyExistsException;
import com.kitapsepeti.catalog.mapper.AuthorMapper;
import com.kitapsepeti.catalog.repository.AuthorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Yazar yönetimi; {@link PublisherAdminService} ile aynı kurallar. Kitabı olan yazar
 * {@code fk_book_authors_author} ile reddedilir (409 RESOURCE_IN_USE).
 */
@Service
@Transactional
public class AuthorAdminService {

	private static final int MAX_SLUG_LENGTH = 160;

	private final AuthorRepository authorRepository;

	public AuthorAdminService(AuthorRepository authorRepository) {
		this.authorRepository = authorRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<AuthorResponse> list(AdminPageRequest page) {
		return PageResponse.of(this.authorRepository.findAll(AdminPaging.byNameThenId(page)), AuthorMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public AuthorResponse get(UUID id) {
		return AuthorMapper.toResponse(require(id));
	}

	public AuthorResponse create(CreateAuthorRequest request) {
		String slug = Slugs.forCreate(request.slug(), request.name(), MAX_SLUG_LENGTH);
		if (this.authorRepository.existsBySlug(slug)) {
			throw new SlugAlreadyExistsException();
		}
		return AuthorMapper.toResponse(this.authorRepository.saveAndFlush(new Author(request.name(), slug)));
	}

	public AuthorResponse update(UUID id, UpdateAuthorRequest request) {
		Author author = require(id);
		if (request.name() != null) {
			author.setName(request.name());
		}
		if (request.slug() != null && !request.slug().equals(author.getSlug())) {
			if (this.authorRepository.existsBySlug(request.slug())) {
				throw new SlugAlreadyExistsException();
			}
			author.setSlug(request.slug());
		}
		this.authorRepository.flush();
		return AuthorMapper.toResponse(author);
	}

	public void delete(UUID id) {
		this.authorRepository.delete(require(id));
		this.authorRepository.flush();
	}

	private Author require(UUID id) {
		return this.authorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Author not found."));
	}

}
