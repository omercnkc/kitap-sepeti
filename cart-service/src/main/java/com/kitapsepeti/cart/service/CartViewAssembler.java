package com.kitapsepeti.cart.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.kitapsepeti.cart.client.CatalogBook;
import com.kitapsepeti.cart.client.CatalogGateway;
import com.kitapsepeti.cart.dto.response.CartLineResponse;
import com.kitapsepeti.cart.dto.response.CartResponse;
import com.kitapsepeti.cart.dto.response.CatalogStatus;
import com.kitapsepeti.cart.exception.CatalogUnavailableException;
import com.kitapsepeti.common.error.ProblemDetails;
import com.kitapsepeti.common.web.RequestPathMasker;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Sepet içeriğini Catalog'un güncel fiyat/stok bilgisiyle birleştirip {@link CartResponse} üretir. DB transaction'ı
 * kapandıktan sonra çağrılır. Catalog'a ulaşılamazsa hata fırlatmaz: sepet anlık görüntü fiyatlarıyla
 * {@link CatalogStatus#UNAVAILABLE} olarak döner. Catalog'un beklenmedik reddi ({@link IllegalStateException})
 * yukarı çıkar (500).
 */
@Component
public class CartViewAssembler {

	private static final Logger log = LoggerFactory.getLogger(CartViewAssembler.class);

	private static final int MONEY_SCALE = 2;

	private final CatalogGateway catalog;

	private final RequestPathMasker pathMasker;

	public CartViewAssembler(CatalogGateway catalog, RequestPathMasker pathMasker) {
		this.catalog = catalog;
		this.pathMasker = pathMasker;
	}

	/** Boş içerik için Catalog çağrılmaz. */
	public CartResponse assemble(CartContents contents) {
		if (contents.lines().isEmpty()) {
			return CartResponse.empty();
		}
		List<UUID> bookIds = contents.lines().stream().map(CartContents.Line::bookId).toList();
		Map<UUID, CatalogBook> books;
		try {
			books = this.catalog.lookup(bookIds);
		}
		catch (CatalogUnavailableException ex) {
			logUnavailable(ex);
			books = null;
		}
		return build(contents, books);
	}

	/** @param books Catalog'da satışta olan kitaplar; null ise Catalog'a ulaşılamadı */
	private static CartResponse build(CartContents contents, Map<UUID, CatalogBook> books) {
		List<CartLineResponse> items = new ArrayList<>(contents.lines().size());
		int itemCount = 0;
		BigDecimal subtotal = money(BigDecimal.ZERO);
		for (CartContents.Line line : contents.lines()) {
			CartLineResponse item = line(line, books);
			items.add(item);
			itemCount += item.quantity();
			if (!Boolean.FALSE.equals(item.available())) {
				subtotal = subtotal.add(item.lineTotal());
			}
		}
		String currency = commonCurrency(items);
		return new CartResponse(items, items.size(), itemCount, (currency != null) ? subtotal : null, currency,
				(books != null) ? CatalogStatus.VERIFIED : CatalogStatus.UNAVAILABLE);
	}

	private static CartLineResponse line(CartContents.Line line, Map<UUID, CatalogBook> books) {
		BigDecimal snapshot = money(line.unitPrice());
		BigDecimal current = null;
		Boolean available = null;
		if (books != null) {
			CatalogBook book = books.get(line.bookId());
			available = (book != null) && book.inStock();
			current = available ? money(book.priceAmount()) : null;
		}
		boolean priceChanged = (current != null) && current.compareTo(snapshot) != 0;
		BigDecimal unitPrice = (current != null) ? current : snapshot;
		BigDecimal lineTotal = money(unitPrice.multiply(BigDecimal.valueOf(line.quantity())));
		return new CartLineResponse(line.bookId(), line.title(), line.coverUrl(), line.quantity(), line.currency(),
				snapshot, current, available, priceChanged, lineTotal);
	}

	/** Tüm satırlar aynı para biriminde ise o; değilse null (farklı birimler toplanmaz). */
	private static String commonCurrency(List<CartLineResponse> items) {
		String currency = items.get(0).currency();
		for (CartLineResponse item : items) {
			if (!Objects.equals(currency, item.currency())) {
				return null;
			}
		}
		return currency;
	}

	private static BigDecimal money(BigDecimal amount) {
		return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}

	/**
	 * Hata handler'ının CATALOG_UNAVAILABLE satırıyla aynı biçim; yalnızca kök nedenin sınıf adı (mesajı URL/host,
	 * istek kitap id'leri içerebilir).
	 */
	private void logUnavailable(CatalogUnavailableException ex) {
		Throwable rootCause = NestedExceptionUtils.getRootCause(ex);
		String note = ((rootCause != null) ? "cause=" + rootCause.getClass().getSimpleName() + ", " : "")
				+ "served from snapshot";
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			HttpServletRequest request = attributes.getRequest();
			ProblemDetails.log(log, ex.getErrorCode(), request, ex, note, this.pathMasker);
		}
		else {
			log.atLevel(ex.getErrorCode().logLevel()).log("cart view -> {} ({})", ex.getErrorCode().name(), note);
		}
	}

}
