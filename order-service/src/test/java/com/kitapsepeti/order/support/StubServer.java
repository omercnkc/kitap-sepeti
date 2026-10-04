package com.kitapsepeti.order.support;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import java.net.InetAddress;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.Notifier;

/**
 * Cart/Catalog/Payment yerine geçen WireMock sunucusu. Port açılışta bir kez seçilir ve sabit kalır: uygulama bağlamı
 * adresi bir kez okur, sunucu "bağlantı reddedildi" senaryosu için durdurulup aynı portta yeniden açılabilir.
 * WireMock'un kendi bildirimleri kapalı (eşleşmeyen istek mesajı başlıkları ve gövdeyi yazar; log testlerini kirletir).
 */
public final class StubServer {

	private static final Notifier SILENT = new Notifier() {

		@Override
		public void info(String message) {
		}

		@Override
		public void error(String message) {
		}

		@Override
		public void error(String message, Throwable t) {
		}

	};

	private final int port;

	private WireMockServer server;

	private StubServer(int port) {
		this.port = port;
		this.server = create(port);
		this.server.start();
	}

	public static StubServer start() {
		return new StubServer(JwksServer.freePort());
	}

	private static WireMockServer create(int port) {
		return new WireMockServer(options().bindAddress(InetAddress.getLoopbackAddress().getHostAddress())
			.port(port)
			.notifier(SILENT));
	}

	public String baseUrl() {
		return "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + this.port;
	}

	public WireMockServer server() {
		return this.server;
	}

	public boolean isRunning() {
		return this.server.isRunning();
	}

	public void stop() {
		this.server.stop();
	}

	/** Durdurulmuşsa aynı portta yeni bir sunucu açar (eşleme ve istek kaydı sıfır). */
	public void ensureRunning() {
		if (!this.server.isRunning()) {
			this.server = create(this.port);
			this.server.start();
		}
	}

}
