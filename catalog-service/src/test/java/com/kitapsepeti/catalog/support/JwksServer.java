package com.kitapsepeti.catalog.support;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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

	private JwksServer(String jwksJson) throws IOException {
		byte[] body = jwksJson.getBytes(StandardCharsets.UTF_8);
		this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
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
		try {
			return new JwksServer(jwksJson);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public String jwkSetUri() {
		InetSocketAddress address = this.server.getAddress();
		return "http://" + address.getAddress().getHostAddress() + ":" + address.getPort() + PATH;
	}

	public int requestCount() {
		return this.requestCount.get();
	}

	@Override
	public void close() {
		this.server.stop(0);
	}

}
