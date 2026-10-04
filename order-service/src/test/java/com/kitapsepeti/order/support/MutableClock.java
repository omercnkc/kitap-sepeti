package com.kitapsepeti.order.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Sistem saati (UTC) + ayarlanabilir fark. Fark sıfırken gerçek saatle aynı; circuit breaker süresi beklemeden atlanır. */
public final class MutableClock extends Clock {

	private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

	public void advance(Duration duration) {
		this.offset.accumulateAndGet(duration, Duration::plus);
	}

	public void reset() {
		this.offset.set(Duration.ZERO);
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException("Test clock is UTC only");
	}

	@Override
	public Instant instant() {
		return Instant.now().plus(this.offset.get());
	}

}
