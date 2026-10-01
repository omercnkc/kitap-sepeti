package com.kitapsepeti.catalog.config;

import com.kitapsepeti.catalog.dto.request.BookSort;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring'in varsayılan enum dönüşümü büyük/küçük harf duyarlı ({@code Enum.valueOf}); {@code sort=price_asc}
 * kabul edilsin diye {@link BookSort} için ayrı converter. Yalnızca bu enum'u etkiler.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addFormatters(FormatterRegistry registry) {
		registry.addConverter(String.class, BookSort.class, BookSort::fromParameter);
	}

}
