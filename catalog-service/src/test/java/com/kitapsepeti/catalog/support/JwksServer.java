package com.kitapsepeti.catalog.support;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

/**
 * user-service'in JWKS ucunu taklit eden, JDK'nın yerleşik HTTP sunucusu (ek bağımlılık yok).
 * Loopback'te rastgele porta bağlanır ve gelen istekleri sayar.
 */
public final class JwksServer implements AutoCloseable {

	private static final String PATH = "/.well-known/jwks.json";

	private final HttpServer server;

	private final AtomicInteger requestCount = new AtomicInteger();

	private JwksServer(String jwksJson, int port) throws IOException {
		byte[] body = jwksJson.getBytes(StandardCharsets.UTF_8);
		this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
		this.server.createContext(PATH, exchange -> {
			this.requestCount.incrementAndGet();
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		this.server.start();
	}

	public static JwksServer start(String jwksJson) {
		return start(jwksJson, 0);
	}

	/** Belirli bir portta açar; kesinti senaryosunda URI önceden bilinip sunucu sonradan başlatılır. */
	public static JwksServer start(String jwksJson, int port) {
		try {
			return new JwksServer(jwksJson, port);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Şu an boş olan bir loopback portu (kapatılıp bırakılır; kısa bir yarış penceresi kabul edilir). */
	public static int freePort() {
		try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
			return socket.getLocalPort();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public static String jwkSetUri(int port) {
		return "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + port + PATH;
	}

	public String jwkSetUri() {
		return jwkSetUri(this.server.getAddress().getPort());
	}

	public int requestCount() {
		return this.requestCount.get();
	}

	@Override
	public void close() {
		this.server.stop(0);
	}

}
