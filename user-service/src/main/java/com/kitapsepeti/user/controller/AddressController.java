package com.kitapsepeti.user.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.user.dto.request.CreateAddressRequest;
import com.kitapsepeti.user.dto.request.UpdateAddressRequest;
import com.kitapsepeti.user.dto.response.AddressResponse;
import com.kitapsepeti.user.security.CurrentUserId;
import com.kitapsepeti.user.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Oturum sahibinin adresleri; kullanıcı id'si yalnızca token'dan gelir, path'te yalnızca adres id'si var. */
@RestController
@RequestMapping("/api/me/addresses")
public class AddressController {

	private static final String BASE_PATH = "/api/me/addresses/";

	private final AddressService addressService;

	public AddressController(AddressService addressService) {
		this.addressService = addressService;
	}

	@GetMapping
	public List<AddressResponse> list(@CurrentUserId UUID userId) {
		return addressService.list(userId);
	}

	@GetMapping("/{id}")
	public AddressResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
		return addressService.get(userId, id);
	}

	@PostMapping
	public ResponseEntity<AddressResponse> create(@CurrentUserId UUID userId,
			@Valid @RequestBody CreateAddressRequest request) {
		AddressResponse created = addressService.create(userId, request);
		return ResponseEntity.created(URI.create(BASE_PATH + created.id())).body(created);
	}

	@PatchMapping("/{id}")
	public AddressResponse update(@CurrentUserId UUID userId, @PathVariable UUID id,
			@Valid @RequestBody UpdateAddressRequest request) {
		return addressService.update(userId, id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
		addressService.delete(userId, id);
		return ResponseEntity.noContent().build();
	}

}
