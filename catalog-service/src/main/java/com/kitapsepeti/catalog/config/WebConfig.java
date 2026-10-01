package com.kitapsepeti.catalog.config;

import com.kitapsepeti.catalog.dto.request.BookSort;
import com.kitapsepeti.catalog.entity.BookStatus;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring'in varsayılan enum dönüşümü büyük/küçük harf duyarlı ({@code Enum.valueOf}); {@code sort=price_asc}
 * ve {@code status=draft} kabul edilsin diye {@link BookSort} ve {@link BookStatus} için ayrı converter'lar.
 * Yalnızca bu iki enum'u etkiler.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addFormatters(FormatterRegistry registry) {
		registry.addConverter(String.class, BookSort.class, BookSort::fromParameter);
		registry.addConverter(String.class, BookStatus.class, BookStatus::fromParameter);
	}

}
