package com.kitapsepeti.catalog.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link HttpUrl} doğrulayıcısı. */
public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || value.isEmpty()) {
			return true;
		}
		try {
			URI uri = new URI(value);
			String scheme = uri.getScheme();
			if (scheme == null || uri.getHost() == null) {
				return false;
			}
			String lower = scheme.toLowerCase(Locale.ROOT);
			return lower.equals("http") || lower.equals("https");
		}
		catch (URISyntaxException ex) {
			return false;
		}
	}

}
