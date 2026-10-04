package com.kitapsepeti.common.web;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Hata yanıtlarının {@code instance}'ı ve istek log satırları için yol maskeleyici. Servis yol kalıplarını Spring
 * sözdizimiyle kaydeder ({@code /api/cart/items/{bookId}}); kalıba uyan yolda değişken segmentler {@code :<ad>} olur.
 * Eşleşme yalnızca segment yapısına bakar (segment sayısı + sabit segmentler birebir): değer UUID olmasa da maskelenir,
 * MVC dispatch'ine bağlı değildir, güvenlik filtrelerinde de çalışır. Birden çok kalıp uyarsa değişkeni en az olan
 * kazanır (Spring'deki gibi; ör. {@code /api/books/lookup} kaydedilirse {@code /api/books/{bookId}}'den önce gelir),
 * eşitlikte ilk kaydedilen.
 * <p>
 * Hiçbir kalıba uymayan yolda UUID biçimindeki her segment {@value #ID_SEGMENT} olur (güvenlik ağı; kalıp kaydetmeyen
 * servisin varsayılanı {@link #uuidOnly()}). Sorgu dizesi ve fragment hiçbir zaman çıktıya girmez. Çıktı her zaman
 * geçerli bir URI yoludur: path segmentinde izin verilmeyen karakterler yüzde kodlanır.
 * <p>
 * Bean olarak tanımlanırsa ortak handler'lar ({@code ProblemDetailExceptionHandler}, {@code ProblemDetailSecurityHandlers})
 * onu kullanır; filtre ve entry point'lere açık parametre olarak verilir. common'da auto-configuration yoktur.
 */
public final class RequestPathMasker {

	public static final String ID_SEGMENT = ":id";

	private static final RequestPathMasker UUID_ONLY = new RequestPathMasker(List.of());

	private static final Pattern UUID = Pattern
		.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

	private static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");

	private static final Pattern LITERAL = Pattern.compile("[A-Za-z0-9._~-]+");

	private static final char[] HEX = "0123456789ABCDEF".toCharArray();

	private final List<Template> templates;

	private RequestPathMasker(List<Template> templates) {
		this.templates = templates;
	}

	/** Kalıpsız maskeleyici: yalnızca UUID segmentleri {@value #ID_SEGMENT} olur. */
	public static RequestPathMasker uuidOnly() {
		return UUID_ONLY;
	}

	/**
	 * @param patterns {@code /} ile başlayan yol kalıpları; segment ya sabit ({@code [A-Za-z0-9._~-]}) ya da tümüyle
	 *     bir değişkendir ({@code {ad}}); joker, sorgu dizesi ve boş segment kabul edilmez
	 */
	public static RequestPathMasker of(String... patterns) {
		List<Template> templates = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (String pattern : patterns) {
			Template template = Template.parse(pattern);
			if (!seen.add(pattern)) {
				throw new IllegalArgumentException("Path pattern registered more than once: " + pattern);
			}
			templates.add(template);
		}
		return new RequestPathMasker(List.copyOf(templates));
	}

	/** Kayıt sırasıyla kalıplar (kapsama testleri için). */
	public List<String> patterns() {
		return this.templates.stream().map(template -> template.pattern).toList();
	}

	/**
	 * Bir uç eşleme kalıbının ({@code @GetMapping("/api/books/{id}")} gibi; değişken adı ve regex kısıtı önemsiz)
	 * aynı segment yapısında bir kayıtlı kalıpla karşılandığını söyler: segment sayısı eşit, değişkenler aynı yerde,
	 * sabit segmentler birebir. Joker ({@code *}, {@code **}, {@code {*ad}}) içeren kalıp karşılanmaz.
	 */
	public boolean covers(String mappingPattern) {
		if (mappingPattern == null || mappingPattern.contains("*")) {
			return false;
		}
		String[] segments = mappingPattern.split("/", -1);
		for (Template template : this.templates) {
			if (template.hasShapeOf(segments)) {
				return true;
			}
		}
		return false;
	}

	/** Context path korunur (maskelenmez), kalan yol {@link #mask(String)} ile maskelenir. */
	public String mask(HttpServletRequest request) {
		String uri = request.getRequestURI();
		if (uri == null) {
			return null;
		}
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
			return encodePath(contextPath) + mask(uri.substring(contextPath.length()));
		}
		return mask(uri);
	}

	public String mask(String path) {
		if (path == null) {
			return null;
		}
		String rawPath = stripQueryAndFragment(path);
		boolean trailingSlash = rawPath.length() > 1 && rawPath.endsWith("/");
		String[] segments = (trailingSlash ? rawPath.substring(0, rawPath.length() - 1) : rawPath).split("/", -1);
		Template template = bestMatch(segments);
		StringBuilder masked = new StringBuilder(rawPath.length());
		for (int i = 0; i < segments.length; i++) {
			if (i > 0) {
				masked.append('/');
			}
			masked.append(maskSegment(segments[i], (template != null) ? template.variables[i] : null, template != null));
		}
		if (trailingSlash) {
			masked.append('/');
		}
		return masked.toString();
	}

	private Template bestMatch(String[] segments) {
		Template best = null;
		for (Template template : this.templates) {
			if (template.matches(segments) && (best == null || template.variableCount < best.variableCount)) {
				best = template;
			}
		}
		return best;
	}

	private static String maskSegment(String segment, String variable, boolean matched) {
		if (variable != null) {
			return ":" + variable;
		}
		if (!matched && UUID.matcher(segment).matches()) {
			return ID_SEGMENT;
		}
		return encodeSegment(segment);
	}

	private static String stripQueryAndFragment(String path) {
		int end = path.length();
		int query = path.indexOf('?');
		if (query >= 0) {
			end = query;
		}
		int fragment = path.indexOf('#');
		if (fragment >= 0 && fragment < end) {
			end = fragment;
		}
		return path.substring(0, end);
	}

	private static String encodePath(String path) {
		String[] segments = path.split("/", -1);
		StringBuilder encoded = new StringBuilder(path.length());
		for (int i = 0; i < segments.length; i++) {
			if (i > 0) {
				encoded.append('/');
			}
			encoded.append(encodeSegment(segments[i]));
		}
		return encoded.toString();
	}

	/** RFC 3986 {@code pchar} dışındaki karakterleri (UTF-8 baytlarıyla) ve tek başına {@code %}'yi yüzde kodlar. */
	private static String encodeSegment(String segment) {
		StringBuilder encoded = null;
		for (int i = 0; i < segment.length(); i++) {
			char c = segment.charAt(i);
			boolean validEscape = c == '%' && i + 2 < segment.length() && isHex(segment.charAt(i + 1))
					&& isHex(segment.charAt(i + 2));
			if (isPchar(c) || validEscape) {
				if (encoded != null) {
					encoded.append(c);
				}
				continue;
			}
			if (encoded == null) {
				encoded = new StringBuilder(segment.length() + 16).append(segment, 0, i);
			}
			int codePoint = segment.codePointAt(i);
			int charCount = Character.charCount(codePoint);
			for (byte b : new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8)) {
				encoded.append('%').append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
			}
			i += charCount - 1;
		}
		return (encoded != null) ? encoded.toString() : segment;
	}

	private static boolean isPchar(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
				|| "-._~!$&'()*+,;=:@".indexOf(c) >= 0;
	}

	private static boolean isHex(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	/** Ayrıştırılmış kalıp: {@code literals[i]} sabit segment ya da null; {@code variables[i]} değişken adı ya da null. */
	private static final class Template {

		private final String pattern;

		private final String[] literals;

		private final String[] variables;

		private final int variableCount;

		private Template(String pattern, String[] literals, String[] variables, int variableCount) {
			this.pattern = pattern;
			this.literals = literals;
			this.variables = variables;
			this.variableCount = variableCount;
		}

		static Template parse(String pattern) {
			if (pattern == null || !pattern.startsWith("/") || (pattern.length() > 1 && pattern.endsWith("/"))) {
				throw new IllegalArgumentException("Path pattern must start with '/' and must not end with '/': " + pattern);
			}
			String[] segments = pattern.split("/", -1);
			String[] literals = new String[segments.length];
			String[] variables = new String[segments.length];
			int variableCount = 0;
			Set<String> names = new HashSet<>();
			for (int i = 1; i < segments.length; i++) {
				String segment = segments[i];
				Matcher variable = VARIABLE.matcher(segment);
				if (variable.matches()) {
					if (!names.add(variable.group(1))) {
						throw new IllegalArgumentException("Duplicate variable in path pattern: " + pattern);
					}
					variables[i] = variable.group(1);
					variableCount++;
				}
				else if (LITERAL.matcher(segment).matches()) {
					literals[i] = segment;
				}
				else {
					throw new IllegalArgumentException("Unsupported segment '" + segment + "' in path pattern: " + pattern);
				}
			}
			literals[0] = "";
			return new Template(pattern, literals, variables, variableCount);
		}

		/** Eşleme kalıbı segmentleri: {@code {...}} değişken, diğerleri sabit. */
		boolean hasShapeOf(String[] mappingSegments) {
			if (mappingSegments.length != this.literals.length) {
				return false;
			}
			for (int i = 1; i < mappingSegments.length; i++) {
				String segment = mappingSegments[i];
				boolean variable = segment.length() > 2 && segment.startsWith("{") && segment.endsWith("}");
				if (variable != (this.variables[i] != null)) {
					return false;
				}
				if (!variable && !this.literals[i].equals(segment)) {
					return false;
				}
			}
			return true;
		}

		boolean matches(String[] segments) {
			if (segments.length != this.literals.length) {
				return false;
			}
			for (int i = 0; i < segments.length; i++) {
				if (this.variables[i] != null) {
					if (segments[i].isEmpty()) {
						return false;
					}
				}
				else if (!this.literals[i].equals(segments[i])) {
					return false;
				}
			}
			return true;
		}

	}

}
