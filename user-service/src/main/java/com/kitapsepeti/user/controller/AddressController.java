package com.kitapsepeti.user.controller;

import static com.kitapsepeti.user.config.OpenApiConfig.PROBLEM_JSON;
import static com.kitapsepeti.user.config.OpenApiConfig.PROBLEM_SCHEMA_REF;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.user.dto.request.CreateAddressRequest;
import com.kitapsepeti.user.dto.request.UpdateAddressRequest;
import com.kitapsepeti.user.dto.response.AddressResponse;
import com.kitapsepeti.user.security.CurrentUserId;
import com.kitapsepeti.user.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Addresses", description = "Oturum sahibinin adresleri. Adresi olan kullanıcının tam olarak bir "
		+ "varsayılan adresi vardır.")
@ApiResponse(responseCode = "403", description = "`ACCOUNT_SUSPENDED`: hesap askıya alınmış.",
		content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_SCHEMA_REF)))
public class AddressController {

	private static final String BASE_PATH = "/api/me/addresses/";

	private final AddressService addressService;

	public AddressController(AddressService addressService) {
		this.addressService = addressService;
	}

	@GetMapping
	@Operation(operationId = "listAddresses", summary = "Adreslerimi listele",
			description = "Önce varsayılan adres, sonra en yeniden eskiye.")
	@ApiResponse(responseCode = "200", description = "Adres listesi (boş olabilir).")
	public List<AddressResponse> list(@CurrentUserId UUID userId) {
		return addressService.list(userId);
	}

	@GetMapping("/{id}")
	@Operation(operationId = "getAddress", summary = "Adres getir")
	@ApiResponse(responseCode = "200", description = "Adres.")
	public AddressResponse get(@CurrentUserId UUID userId,
			@Parameter(description = "Adres id'si") @PathVariable UUID id) {
		return addressService.get(userId, id);
	}

	@PostMapping
	@Operation(operationId = "createAddress", summary = "Adres ekle",
			description = "`isDefault: true` ise önceki varsayılan adres varsayılanlıktan çıkar. "
					+ "Kullanıcının ilk adresi her durumda varsayılan olur.")
	@ApiResponse(responseCode = "201", description = "Adres oluşturuldu.",
			headers = @Header(name = "Location", description = "Yeni adresin yolu.",
					schema = @Schema(type = "string", format = "uri-reference", example = "/api/me/addresses/01a0ed1b-22f7-7a76-aa96-c8ec0f3b3a4a")))
	public ResponseEntity<AddressResponse> create(@CurrentUserId UUID userId,
			@Valid @RequestBody CreateAddressRequest request) {
		AddressResponse created = addressService.create(userId, request);
		return ResponseEntity.created(URI.create(BASE_PATH + created.id())).body(created);
	}

	@PatchMapping("/{id}")
	@Operation(operationId = "updateAddress", summary = "Adres güncelle",
			description = "Kısmi güncelleme: yalnızca gönderilen alanlar değişir. "
					+ "`isDefault: true` bu adresi varsayılan yapar (öncekini çıkarır).")
	@ApiResponse(responseCode = "200", description = "Güncellenmiş adres.")
	@ApiResponse(responseCode = "409", description = "`DEFAULT_ADDRESS_REQUIRED`: varsayılan adrese `isDefault: false` "
			+ "gönderildi; önce başka bir adresi varsayılan yapın.",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_SCHEMA_REF)))
	public AddressResponse update(@CurrentUserId UUID userId,
			@Parameter(description = "Adres id'si") @PathVariable UUID id,
			@Valid @RequestBody UpdateAddressRequest request) {
		return addressService.update(userId, id, request);
	}

	@DeleteMapping("/{id}")
	@Operation(operationId = "deleteAddress", summary = "Adres sil",
			description = "Silinen adres varsayılansa, kalan en yeni adres varsayılan olur.")
	@ApiResponse(responseCode = "204", description = "Silindi.")
	public ResponseEntity<Void> delete(@CurrentUserId UUID userId,
			@Parameter(description = "Adres id'si") @PathVariable UUID id) {
		addressService.delete(userId, id);
		return ResponseEntity.noContent().build();
	}

}
