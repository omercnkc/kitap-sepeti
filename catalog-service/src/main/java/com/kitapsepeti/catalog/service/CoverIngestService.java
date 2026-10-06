package com.kitapsepeti.catalog.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.kitapsepeti.catalog.config.S3Properties;
import com.kitapsepeti.catalog.storage.CoverStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dış kapak URL'sini allowlist üzerinden indirip kendi bucket'ına koyar (SSRF: yalnızca https,
 * host allowlist, yönlendirme yok). Başarısızlıkta WARN + null; kitap yine oluşur.
 */
@Service
public class CoverIngestService {

	private static final Logger log = LoggerFactory.getLogger(CoverIngestService.class);

	private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private final CoverStorage coverStorage;

	private final S3Properties properties;

	private final HttpClient httpClient;

	public CoverIngestService(CoverStorage coverStorage, S3Properties properties) {
		this.coverStorage = coverStorage;
		this.properties = properties;
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(properties.ingestConnectTimeout())
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
	}

	/**
	 * @return yönetilen public URL, zaten yönetilen URL (aynı), veya null (boş / izin yok / hata)
	 */
	public String ingestIfExternal(String coverUrl) {
		if (coverUrl == null || coverUrl.isBlank()) {
			return null;
		}
		String trimmed = coverUrl.trim();
		if (this.coverStorage.isManagedPublicUrl(trimmed)) {
			return trimmed;
		}
		URI uri;
		try {
			uri = URI.create(trimmed);
		}
		catch (IllegalArgumentException ex) {
			log.warn("Cover ingest skipped: invalid URL");
			return null;
		}
		if (!isAllowedUri(uri)) {
			return null;
		}
		try {
			DownloadedImage image = download(uri);
			String key = "covers/" + UUID.randomUUID() + "." + extensionFor(image.contentType());
			return this.coverStorage.put(image.bytes(), image.contentType(), key);
		}
		catch (Exception ex) {
			log.warn("Cover ingest failed: {}", ex.toString());
			return null;
		}
	}

	private boolean isAllowedUri(URI uri) {
		String scheme = uri.getScheme();
		String host = uri.getHost();
		if (scheme == null || host == null) {
			return false;
		}
		String hostLower = host.toLowerCase(Locale.ROOT);
		boolean https = "https".equalsIgnoreCase(scheme);
		boolean localHttp = "http".equalsIgnoreCase(scheme)
				&& ("localhost".equals(hostLower) || "127.0.0.1".equals(hostLower));
		if (!https && !localHttp) {
			return false;
		}
		return this.properties.allowedIngestHosts().stream()
			.anyMatch(allowed -> allowed.equalsIgnoreCase(hostLower));
	}

	private DownloadedImage download(URI uri) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(uri)
			.GET()
			.timeout(this.properties.ingestReadTimeout())
			.header("Accept", "image/jpeg,image/png,image/webp,*/*")
			.build();
		HttpResponse<InputStream> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("HTTP " + response.statusCode());
		}
		String contentType = response.headers().firstValue("Content-Type")
			.map(CoverIngestService::normalizeMediaType)
			.filter(ALLOWED_IMAGE_TYPES::contains)
			.orElseThrow(() -> new IOException("unsupported content-type"));
		try (InputStream in = response.body()) {
			byte[] bytes = readLimited(in, this.properties.maxObjectBytes());
			return new DownloadedImage(bytes, contentType);
		}
	}

	private static byte[] readLimited(InputStream in, long maxBytes) throws IOException {
		byte[] buffer = in.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE));
		if (buffer.length > maxBytes) {
			throw new IOException("object exceeds max size");
		}
		return buffer;
	}

	private static String normalizeMediaType(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String type = raw.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
		if ("image/jpg".equals(type)) {
			return "image/jpeg";
		}
		return type;
	}

	private static String extensionFor(String contentType) {
		return switch (contentType) {
			case "image/png" -> "png";
			case "image/webp" -> "webp";
			default -> "jpg";
		};
	}

	private record DownloadedImage(byte[] bytes, String contentType) {
	}

}
