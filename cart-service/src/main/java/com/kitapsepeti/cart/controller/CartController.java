package com.kitapsepeti.cart.controller;

import static com.kitapsepeti.cart.config.OpenApiConfig.LIMIT_PROBLEM_SCHEMA_REF;
import static com.kitapsepeti.cart.config.OpenApiConfig.PROBLEM_JSON;

import java.util.UUID;

import com.kitapsepeti.cart.config.OpenApiConfig;
import com.kitapsepeti.cart.dto.request.AddCartItemRequest;
import com.kitapsepeti.cart.dto.request.UpdateCartItemRequest;
import com.kitapsepeti.cart.dto.response.CartResponse;
import com.kitapsepeti.cart.security.CurrentUserId;
import com.kitapsepeti.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Oturumdaki kullanıcının sepeti; kullanıcı yalnızca token'dan gelir, başka kullanıcının sepetine yol yoktur. */
@RestController
@RequestMapping(path = "/api/cart", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = OpenApiConfig.TAG_CART, description = "Oturumdaki kullanıcının aktif sepeti. Her yanıt güncel sepetin "
		+ "tamamıdır; fiyat ve stok Catalog'la birleştirilir (`catalogStatus`).")
public class CartController {

	private final CartService cartService;

	public CartController(CartService cartService) {
		this.cartService = cartService;
	}

	@GetMapping
	@Operation(operationId = "getCart", summary = "Sepeti oku",
			description = "Aktif sepet yoksa boş sepet döner (oluşturulmaz, Catalog'a sorulmaz).")
	@ApiResponse(responseCode = "200", description = "Güncel sepet.")
	public CartResponse getCart(@CurrentUserId UUID userId) {
		return this.cartService.getCart(userId);
	}

	/** Sepeti yoksa açar; kitap sepetteyse adedini artırır. Yanıt güncel sepettir (200). */
	@PostMapping(path = "/items", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(operationId = "addCartItem", summary = "Sepete kitap ekle",
			description = "Sepet yoksa açılır. Kitap sepetteyse adet mevcut adede eklenir ve satırın anlık görüntüsü "
					+ "(fiyat, başlık, kapak) Catalog'daki güncel haliyle yenilenir. Kitap Catalog'da doğrulanır; doğrulanamazsa sepete dokunulmaz.")
	@ApiResponse(responseCode = "200", description = "Güncel sepet.")
	@ApiResponse(responseCode = "409", description = "`BOOK_NOT_AVAILABLE` (kitap yok, yayında değil ya da stokta "
			+ "değil), `CART_LINE_LIMIT_EXCEEDED` (farklı kitap sayısı sınırı) veya `CART_QUANTITY_LIMIT_EXCEEDED` "
			+ "(kitap başına adet sınırı; mevcut adet + eklenen). Limit hatalarında `limit` döner. Sepet değişmez.",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = LIMIT_PROBLEM_SCHEMA_REF),
					examples = @ExampleObject(value = """
							{"title":"Conflict","status":409,"detail":"Quantity exceeds the maximum allowed per book.",\
							"instance":"/api/cart/items","code":"CART_QUANTITY_LIMIT_EXCEEDED","limit":10}""")))
	@ApiResponse(responseCode = "503", description = "`CATALOG_UNAVAILABLE`: kitap doğrulanamadı çünkü Catalog'a "
			+ "ulaşılamıyor (sepet değişmez) ya da `AUTHENTICATION_UNAVAILABLE`: token doğrulanamadı çünkü "
			+ "user-service JWKS ucuna ulaşılamıyor. İkisinde de daha sonra tekrar deneyin.")
	public CartResponse addItem(@CurrentUserId UUID userId, @Valid @RequestBody AddCartItemRequest request) {
		return this.cartService.addItem(userId, request);
	}

	/** Adedi verilen değere ayarlar; kitap sepette değilse 404. */
	@PatchMapping(path = "/items/{bookId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(operationId = "changeCartItemQuantity", summary = "Adedi değiştir",
			description = "Adet verilen değere ayarlanır (eklenmez). Değişiklik Catalog doğrulaması istemez; satırın "
					+ "anlık görüntüsü korunur. "
					+ "Aynı adet gönderilirse sepet değişmez.")
	@ApiResponse(responseCode = "200", description = "Güncel sepet.")
	@ApiResponse(responseCode = "404", description = "`RESOURCE_NOT_FOUND`: aktif sepet yok ya da kitap sepette değil.")
	@ApiResponse(responseCode = "409", description = "`CART_QUANTITY_LIMIT_EXCEEDED`: adet kitap başına sınırı "
			+ "aşıyor; `limit` döner. Satır değişmez.",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = LIMIT_PROBLEM_SCHEMA_REF)))
	public CartResponse changeQuantity(@CurrentUserId UUID userId,
			@Parameter(description = "Sepetteki kitabın id'si") @PathVariable UUID bookId,
			@Valid @RequestBody UpdateCartItemRequest request) {
		return this.cartService.changeQuantity(userId, bookId, request);
	}

	/** Idempotent: kitap sepette değilse de 200. */
	@DeleteMapping("/items/{bookId}")
	@Operation(operationId = "removeCartItem", summary = "Kitabı sepetten çıkar",
			description = "Idempotent: kitap sepette değilse ya da sepet yoksa da 200 (sepet oluşturulmaz).")
	@ApiResponse(responseCode = "200", description = "Güncel sepet.")
	public CartResponse removeItem(@CurrentUserId UUID userId,
			@Parameter(description = "Kitabın id'si") @PathVariable UUID bookId) {
		return this.cartService.removeItem(userId, bookId);
	}

	/** Sepeti boşaltır; sepet aktif kalır. */
	@DeleteMapping("/items")
	@Operation(operationId = "clearCart", summary = "Sepeti boşalt",
			description = "Tüm satırlar silinir, sepet aktif kalır. Sepet yoksa oluşturulmaz. Yanıt her zaman boş sepet.")
	@ApiResponse(responseCode = "200", description = "Boş sepet.")
	public CartResponse clear(@CurrentUserId UUID userId) {
		return this.cartService.clear(userId);
	}

}
