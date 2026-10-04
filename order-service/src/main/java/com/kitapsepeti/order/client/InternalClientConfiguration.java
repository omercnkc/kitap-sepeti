package com.kitapsepeti.order.client;

import feign.Logger;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * Cart, Catalog ve Payment Feign istemcilerinin kendi yapılandırması ({@code @FeignClient(configuration = ...)}); her
 * istemcinin alt bağlamında ayrı kurulur. BİLEREK {@code @Configuration} değil: bileşen taramasına girseydi bean'leri
 * bütün Feign istemcilerine global uygulanırdı.
 */
public class InternalClientConfiguration {

	@Bean
	RequestInterceptor internalApiKeyInterceptor(InternalApiKey key) {
		return new InternalApiKeyInterceptor(key);
	}

	@Bean
	ErrorDecoder problemErrorDecoder() {
		return new ProblemErrorDecoder();
	}

	/** BASIC ve üstü URL'yi (sipariş id'si), FULL başlıkları (anahtar) ve gövdeyi loglar. */
	@Bean
	Logger.Level feignLoggerLevel() {
		return Logger.Level.NONE;
	}

	/** Otomatik tekrar yok: tekrar kararı (idempotent istekle) çağıranındır. */
	@Bean
	Retryer feignRetryer() {
		return Retryer.NEVER_RETRY;
	}

}
