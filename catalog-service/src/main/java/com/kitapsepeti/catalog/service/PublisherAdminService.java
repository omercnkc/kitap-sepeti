package com.kitapsepeti.catalog.service;

import java.util.UUID;

import com.kitapsepeti.catalog.dto.request.AdminPageRequest;
import com.kitapsepeti.catalog.dto.request.CreatePublisherRequest;
import com.kitapsepeti.catalog.dto.request.UpdatePublisherRequest;
import com.kitapsepeti.catalog.dto.response.PageResponse;
import com.kitapsepeti.catalog.dto.response.PublisherResponse;
import com.kitapsepeti.catalog.entity.Publisher;
import com.kitapsepeti.catalog.exception.SlugAlreadyExistsException;
import com.kitapsepeti.catalog.exception.InvalidFieldException;
import com.kitapsepeti.catalog.mapper.PublisherMapper;
import com.kitapsepeti.catalog.repository.PublisherRepository;
import com.kitapsepeti.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Yayınevi yönetimi. Slug ön kontrolü (existsBySlug) yarışta yetmez; o durumda {@code uk_publishers_slug}
 * ihlali flush'ta oluşur ve aynı 409 SLUG_ALREADY_EXISTS'e çevrilir. Silmede kitabı olan yayınevi
 * {@code fk_books_publisher} ile reddedilir (409 RESOURCE_IN_USE). Kitap formundan gelen isim
 * {@link #ensureByName} ile find-or-create edilir (mevcut ad global rename edilmez).
 */
@Service
@Transactional
public class PublisherAdminService {

	private static final int MAX_SLUG_LENGTH = 160;

	private final PublisherRepository publisherRepository;

	public PublisherAdminService(PublisherRepository publisherRepository) {
		this.publisherRepository = publisherRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<PublisherResponse> list(AdminPageRequest page) {
		return PageResponse.of(this.publisherRepository.findAll(AdminPaging.byNameThenId(page)),
				PublisherMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public PublisherResponse get(UUID id) {
		return PublisherMapper.toResponse(require(id));
	}

	/**
	 * İsme göre yayınevi bulur veya oluşturur (slug addan, tr-TR uyumlu). Bulunan kaydın
	 * {@code name} alanı değiştirilmez.
	 */
	public Publisher ensureByName(String name) {
		String slug;
		try {
			slug = Slugs.forCreate(null, name, MAX_SLUG_LENGTH);
		}
		catch (InvalidFieldException ex) {
			throw new InvalidFieldException("publisherName", ex.getFieldMessage());
		}
		return this.publisherRepository.findBySlug(slug)
			.orElseGet(() -> this.publisherRepository.saveAndFlush(new Publisher(name, slug)));
	}

	public PublisherResponse create(CreatePublisherRequest request) {
		String slug = Slugs.forCreate(request.slug(), request.name(), MAX_SLUG_LENGTH);
		if (this.publisherRepository.existsBySlug(slug)) {
			throw new SlugAlreadyExistsException();
		}
		return PublisherMapper.toResponse(this.publisherRepository.saveAndFlush(new Publisher(request.name(), slug)));
	}

	/** flush: güncel {@code updatedAt} yanıta girsin ve kısıt ihlali burada oluşsun. */
	public PublisherResponse update(UUID id, UpdatePublisherRequest request) {
		Publisher publisher = require(id);
		if (request.name() != null) {
			publisher.setName(request.name());
		}
		if (request.slug() != null && !request.slug().equals(publisher.getSlug())) {
			if (this.publisherRepository.existsBySlug(request.slug())) {
				throw new SlugAlreadyExistsException();
			}
			publisher.setSlug(request.slug());
		}
		this.publisherRepository.flush();
		return PublisherMapper.toResponse(publisher);
	}

	/** flush: FK ihlali commit'te değil burada, transaction açıkken oluşsun. */
	public void delete(UUID id) {
		this.publisherRepository.delete(require(id));
		this.publisherRepository.flush();
	}

	private Publisher require(UUID id) {
		return this.publisherRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Publisher not found."));
	}

}
