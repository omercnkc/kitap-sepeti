package com.kitapsepeti.order.entity;

/** DB'deki değeri sabit olan enum (kolon {@code utf8mb4_bin}; CHECK yalnızca bu yazımı kabul eder). */
public interface DbEnum {

	String dbValue();

}
