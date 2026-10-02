package com.kitapsepeti.cart.support;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Hibernate'in çalıştırdığı SQL'i kaydeder (application-test.yml'de {@code statement_inspector}). Yalnızca
 * {@link #start()} ile {@link #stop()} arasında kayıt tutar; diğer testlerde bellek büyümez.
 */
public class SqlCapture implements StatementInspector {

	private static final List<String> STATEMENTS = new ArrayList<>();

	private static volatile boolean recording;

	@Override
	public String inspect(String sql) {
		if (recording) {
			synchronized (STATEMENTS) {
				STATEMENTS.add(sql);
			}
		}
		return sql;
	}

	public static void start() {
		synchronized (STATEMENTS) {
			STATEMENTS.clear();
		}
		recording = true;
	}

	/** Kaydı durdurur ve o ana kadar kaydedilenleri döndürür. */
	public static List<String> stop() {
		recording = false;
		synchronized (STATEMENTS) {
			return List.copyOf(STATEMENTS);
		}
	}

}
