package com.kitapsepeti.catalog.controller.admin;

import java.net.URI;
import java.util.UUID;

import com.kitapsepeti.catalog.dto.request.AdminPageRequest;
import com.kitapsepeti.catalog.dto.request.CreateAuthorRequest;
import com.kitapsepeti.catalog.dto.request.UpdateAuthorRequest;
import com.kitapsepeti.catalog.dto.response.AuthorResponse;
import com.kitapsepeti.catalog.dto.response.PageResponse;
import com.kitapsepeti.catalog.service.AuthorAdminService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Yazar yönetimi (SecurityConfig: /api/admin/** yalnızca ADMIN). */
@RestController
@RequestMapping(path = "/api/admin/authors", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminAuthorController {

	private static final String BASE_PATH = "/api/admin/authors/";

	private final AuthorAdminService authorAdminService;

	public AdminAuthorController(AuthorAdminService authorAdminService) {
		this.authorAdminService = authorAdminService;
	}

	@GetMapping
	public PageResponse<AuthorResponse> list(@Valid @ModelAttribute AdminPageRequest page) {
		return authorAdminService.list(page);
	}

	@GetMapping("/{id}")
	public AuthorResponse get(@PathVariable UUID id) {
		return authorAdminService.get(id);
	}

	@PostMapping
	public ResponseEntity<AuthorResponse> create(@Valid @RequestBody CreateAuthorRequest request) {
		AuthorResponse created = authorAdminService.create(request);
		return ResponseEntity.created(URI.create(BASE_PATH + created.id())).body(created);
	}

	@PatchMapping("/{id}")
	public AuthorResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateAuthorRequest request) {
		return authorAdminService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		authorAdminService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
