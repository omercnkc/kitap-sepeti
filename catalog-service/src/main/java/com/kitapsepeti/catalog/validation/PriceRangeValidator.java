package com.kitapsepeti.catalog.validation;

import com.kitapsepeti.catalog.dto.request.BookSearchRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link ValidPriceRange} doğrulayıcısı; sınırlardan biri yoksa geçerli. */
public class PriceRangeValidator implements ConstraintValidator<ValidPriceRange, BookSearchRequest> {

	@Override
	public boolean isValid(BookSearchRequest request, ConstraintValidatorContext context) {
		if (request == null || request.minPrice() == null || request.maxPrice() == null
				|| request.minPrice().compareTo(request.maxPrice()) <= 0) {
			return true;
		}
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
			.addPropertyNode("minPrice")
			.addConstraintViolation();
		return false;
	}

}
