package com.kitapsepeti.cart.support;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Catalog'u taklit eden JDK HTTP sunucusu (ek bağımlılık yok; {@link JwksServer} kalıbı). Loopback'te rastgele porta
 * bağlanır, gelen her isteği (yol, ham query, header'lar) kaydeder ve yanıtı test belirler. İstekler ayrı thread'lerde
 * işlenir: zaman aşımı testinde bekleyen yanıt sonraki testin isteğini bekletmez.
 */
public final class CatalogStub implements AutoCloseable {

	public static final String PROBLEM_JSON = "application/problem+json";

	private static final Response NOT_CONFIGURED = Response.raw(599, "application/json", "{\"stub\":\"not configured\"}");

	private final HttpServer server;

	private final ExecutorService executor;

	private final List<Request> requests = new CopyOnWriteArrayList<>();

	private volatile Function<Request, Response> responder = request -> NOT_CONFIGURED;

	private CatalogStub() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		this.executor = Executors.newCachedThreadPool(runnable -> {
			Thread thread = new Thread(runnable, "catalog-stub");
			thread.setDaemon(true);
			return thread;
		});
		this.server.setExecutor(this.executor);
		this.server.createContext("/", this::handle);
		this.server.start();
	}

	public static CatalogStub start() {
		try {
			return new CatalogStub();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public String baseUrl() {
		return "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + this.server.getAddress().getPort();
	}

	public int port() {
		return this.server.getAddress().getPort();
	}

	/** Kayıtları siler, yanıtı "yapılandırılmadı"ya (599) döndürür. */
	public void reset() {
		this.requests.clear();
		this.responder = request -> NOT_CONFIGURED;
	}

	public void respond(Response response) {
		this.responder = request -> response;
	}

	public void respondWith(Function<Request, Response> responder) {
		this.responder = responder;
	}

	public List<Request> requests() {
		return List.copyOf(this.requests);
	}

	private void handle(HttpExchange exchange) throws IOException {
		Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, List.copyOf(values)));
		Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
				exchange.getRequestURI().getRawQuery(), headers);
		this.requests.add(request);
		Response response = this.responder.apply(request);
		try (exchange) {
			if (!response.delay().isZero()) {
				Thread.sleep(response.delay());
			}
			byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", response.contentType());
			response.headers().forEach(exchange.getResponseHeaders()::set);
			exchange.sendResponseHeaders(response.status(), (body.length == 0) ? -1 : body.length);
			if (body.length > 0) {
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(body);
				}
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		catch (IOException ex) {
			// İstemci zaman aşımıyla bağlantıyı kapattıysa yazma başarısız olur; beklenen durum.
		}
	}

	@Override
	public void close() {
		this.server.stop(0);
		this.executor.shutdownNow();
	}

	/** Stub'a gelen istek; header adları büyük/küçük harf duyarsız. */
	public record Request(String method, String path, String query, Map<String, List<String>> headers) {

		public boolean hasHeader(String name) {
			return this.headers.containsKey(name);
		}

	}

	public record Response(int status, String contentType, String body, Duration delay, Map<String, String> headers) {

		/** Problem yanıtlarının gövdesinde; loga ya da cart yanıtına sızmamalı. */
		public static final String BODY_MARKER = "stub-govde-detayi-8841";

		public static Response json(int status, String body) {
			return new Response(status, "application/json", body, Duration.ZERO, Map.of());
		}

		public static Response problem(int status) {
			return new Response(status, PROBLEM_JSON,
					"{\"status\":" + status + ",\"code\":\"STUB\",\"detail\":\"" + BODY_MARKER + "\"}", Duration.ZERO,
					Map.of());
		}

		public static Response raw(int status, String contentType, String body) {
			return new Response(status, contentType, body, Duration.ZERO, Map.of());
		}

		public Response delayed(Duration delay) {
			return new Response(this.status, this.contentType, this.body, delay, this.headers);
		}

		public Response withHeader(String name, String value) {
			Map<String, String> merged = new TreeMap<>(this.headers);
			merged.put(name, value);
			return new Response(this.status, this.contentType, this.body, this.delay, Map.copyOf(merged));
		}

	}

}
