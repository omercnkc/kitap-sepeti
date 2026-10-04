package com.kitapsepeti.payment.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Varsayılan olarak sistem saati; test {@link #fixAt} ile sabitleyip {@link #advance} ile ilerletebilir (cart kopyası). */
public class MutableClock extends Clock {

	private volatile Instant fixed;

	public void fixAt(Instant instant) {
		this.fixed = instant;
	}

	public void advance(Duration duration) {
		this.fixed = instant().plus(duration);
	}

	public void useSystemTime() {
		this.fixed = null;
	}

	@Override
	public Instant instant() {
		Instant current = this.fixed;
		return (current != null) ? current : Instant.now();
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException();
	}

}
