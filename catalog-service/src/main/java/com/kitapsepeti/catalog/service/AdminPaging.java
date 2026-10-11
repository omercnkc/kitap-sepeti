package com.kitapsepeti.catalog.service;

import com.kitapsepeti.catalog.dto.request.AdminPageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;

/** Admin listeleri ada, eşitlikte id'ye göre sıralanır (sayfalar arası kararlı sıra). */
final class AdminPaging {

	private static final Sort BY_NAME_THEN_ID = Sort.by(Order.asc("name"), Order.asc("id"));

	private AdminPaging() {
	}

	static Pageable byNameThenId(AdminPageRequest request) {
		return byNameThenId(request.page(), request.size());
	}

	static Pageable byNameThenId(int page, int size) {
		return PageRequest.of(page, size, BY_NAME_THEN_ID);
	}

}
