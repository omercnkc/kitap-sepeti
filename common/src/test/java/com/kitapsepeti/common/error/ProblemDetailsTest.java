package com.kitapsepeti.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kitapsepeti.common.web.RequestPathMasker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

class ProblemDetailsTest {

	private final Logger logger = (Logger) LoggerFactory.getLogger(ProblemDetailsTest.class);

	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@BeforeEach
	void attachAppender() {
		this.appender.start();
		this.logger.addAppender(this.appender);
	}

	@AfterEach
	void detachAppender() {
		this.logger.detachAppender(this.appender);
	}

	@Test
	void createSetsStatusDetailInstanceAndCode() {
		ProblemDetail problem = ProblemDetails.create(CommonErrorCode.RESOURCE_NOT_FOUND, "Book not found.",
				new MockHttpServletRequest("GET", "/api/books/42"));

		assertThat(problem.getStatus()).isEqualTo(404);
		assertThat(problem.getDetail()).isEqualTo("Book not found.");
		assertThat(problem.getInstance()).isEqualTo(URI.create("/api/books/42"));
		assertThat(problem.getProperties()).containsEntry(ProblemDetails.CODE_PROPERTY, "RESOURCE_NOT_FOUND");
	}

	@Test
	void applyKeepsSpringStatus() {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);

		ProblemDetails.apply(problem, CommonErrorCode.CONFLICT, "x", new MockHttpServletRequest("PUT", "/api/a"));

		assertThat(problem.getStatus()).isEqualTo(405);
		assertThat(problem.getProperties()).containsEntry("code", "CONFLICT");
	}

	/** Eskiden URI'ye çevrilemeyen yolda instance boş kalırdı; maskeleyici artık geçersiz karakterleri kodluyor. */
	@Test
	void unparsableRequestUriIsEncodedIntoAValidInstance() {
		ProblemDetail problem = ProblemDetails.create(CommonErrorCode.NOT_FOUND, "d",
				new MockHttpServletRequest("GET", "/a b|c"));

		assertThat(problem.getInstance()).isEqualTo(URI.create("/a%20b%7Cc"));
		assertThat(problem.getStatus()).isEqualTo(404);
	}

	@Test
	void instanceAndLogUseTheMaskerAndNeverTheQueryString() {
		String id = UUID.randomUUID().toString();
		RequestPathMasker masker = RequestPathMasker.of("/api/cart/items/{bookId}");
		MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/cart/items/" + id);
		request.setQueryString("secret=" + id);

		ProblemDetail problem = ProblemDetails.create(CommonErrorCode.NOT_FOUND, "d", request, masker);
		ProblemDetails.log(this.logger, CommonErrorCode.NOT_FOUND, request, null, null, masker);

		assertThat(problem.getInstance()).isEqualTo(URI.create("/api/cart/items/:bookId"));
		assertThat(this.appender.list).singleElement()
			.extracting(ILoggingEvent::getFormattedMessage)
			.isEqualTo("PATCH /api/cart/items/:bookId -> NOT_FOUND");
	}

	@Test
	void overloadsWithoutMaskerStillApplyTheUuidSafetyNet() {
		String id = UUID.randomUUID().toString().toUpperCase();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/books/" + id + "/x");

		ProblemDetail problem = ProblemDetails.create(CommonErrorCode.NOT_FOUND, "d", request);
		ProblemDetails.log(this.logger, CommonErrorCode.NOT_FOUND, request, null);

		assertThat(problem.getInstance()).isEqualTo(URI.create("/api/books/:id/x"));
		assertThat(this.appender.list).singleElement()
			.extracting(ILoggingEvent::getFormattedMessage)
			.isEqualTo("GET /api/books/:id/x -> NOT_FOUND");
	}

	@Test
	void logLineHasMethodPathCodeAndOnlyErrorCarriesStackTrace() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/x");
		RuntimeException cause = new RuntimeException("secret-looking message");

		ProblemDetails.log(this.logger, CommonErrorCode.CONFLICT, request, cause, "constraint=uk_x");
		ProblemDetails.log(this.logger, CommonErrorCode.INTERNAL_ERROR, request, cause);

		assertThat(this.appender.list).hasSize(2);
		ILoggingEvent conflict = this.appender.list.get(0);
		assertThat(conflict.getLevel().toString()).isEqualTo("INFO");
		assertThat(conflict.getFormattedMessage()).isEqualTo("POST /api/x -> CONFLICT (constraint=uk_x)");
		assertThat(conflict.getThrowableProxy()).isNull();

		ILoggingEvent internal = this.appender.list.get(1);
		assertThat(internal.getLevel().toString()).isEqualTo("ERROR");
		assertThat(internal.getFormattedMessage()).isEqualTo("POST /api/x -> INTERNAL_ERROR");
		assertThat(internal.getThrowableProxy()).isNotNull();
	}

}
