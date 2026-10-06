package com.kitapsepeti.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.kitapsepeti.catalog.config.S3Properties;
import com.kitapsepeti.catalog.storage.InMemoryCoverStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

class CoverIngestServiceTest {

	private static final byte[] TINY_JPEG = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9 };

	private WireMockServer wireMock;

	private InMemoryCoverStorage storage;

	private CoverIngestService service;

	@BeforeEach
	void setUp() {
		this.wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
		this.wireMock.start();
		S3Properties properties = new S3Properties("http://127.0.0.1:9", "test", "testtest", "kitapsepeti-covers",
				"http://localhost:9000", "us-east-1", 5_242_880L, Duration.ofSeconds(1), Duration.ofSeconds(2),
				List.of("covers.openlibrary.org", "localhost", "127.0.0.1"));
		this.storage = new InMemoryCoverStorage(properties);
		this.service = new CoverIngestService(this.storage, properties);
	}

	@AfterEach
	void tearDown() {
		this.wireMock.stop();
	}

	@Test
	void blankOrNullReturnsNull() {
		assertThat(this.service.ingestIfExternal(null)).isNull();
		assertThat(this.service.ingestIfExternal("  ")).isNull();
		assertThat(this.storage.size()).isZero();
	}

	@Test
	void managedUrlReturnedAsIs() {
		String managed = "http://localhost:9000/kitapsepeti-covers/covers/x.jpg";
		assertThat(this.service.ingestIfExternal(managed)).isEqualTo(managed);
		assertThat(this.storage.size()).isZero();
	}

	@Test
	void nonAllowlistedHostReturnsNullWithoutPersist() {
		assertThat(this.service.ingestIfExternal("https://cdn.example.com/kapak.jpg")).isNull();
		assertThat(this.storage.size()).isZero();
	}

	@Test
	void ftpSchemeReturnsNull() {
		assertThat(this.service.ingestIfExternal("ftp://covers.openlibrary.org/b/id/1-L.jpg")).isNull();
		assertThat(this.storage.size()).isZero();
	}

	@Test
	void allowlistedLocalHttpJpegIsIngested() {
		this.wireMock.stubFor(get(urlEqualTo("/b/id/1-L.jpg")).willReturn(aResponse().withStatus(200)
			.withHeader("Content-Type", "image/jpeg")
			.withBody(TINY_JPEG)));

		String url = "http://127.0.0.1:" + this.wireMock.port() + "/b/id/1-L.jpg";
		String result = this.service.ingestIfExternal(url);

		assertThat(result).startsWith("http://localhost:9000/kitapsepeti-covers/covers/");
		assertThat(result).endsWith(".jpg");
		assertThat(this.storage.size()).isEqualTo(1);
	}

	@Test
	void nonImageContentTypeReturnsNull() {
		this.wireMock.stubFor(get(urlEqualTo("/not-image")).willReturn(aResponse().withStatus(200)
			.withHeader("Content-Type", "text/html")
			.withBody("<html></html>")));

		String url = "http://127.0.0.1:" + this.wireMock.port() + "/not-image";
		assertThat(this.service.ingestIfExternal(url)).isNull();
		assertThat(this.storage.size()).isZero();
	}

}
