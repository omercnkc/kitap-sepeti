package com.kitapsepeti.catalog.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.entity.Author;
import com.kitapsepeti.catalog.entity.Category;
import com.kitapsepeti.catalog.entity.Publisher;
import com.kitapsepeti.catalog.repository.AuthorRepository;
import com.kitapsepeti.catalog.repository.CategoryRepository;
import com.kitapsepeti.catalog.repository.PublisherRepository;
import com.kitapsepeti.catalog.support.TestJwt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Worker açıkken (yalnızca relay testlerinde) outbox → RabbitMQ akışı. Her test exchange'e kendi desenine
 * bağlı geçici bir kuyruk açar; yayıncı, gerçek yayıncının etrafında hata enjekte edebilen bir test double'ıdır.
 */
@TestPropertySource(properties = { "app.outbox.enabled=true", "app.outbox.poll-interval=200ms" })
@Import(FaultInjectingPublisher.Config.class)
class OutboxRelayIT extends ApiTestSupport {

	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	private static final String BASE = "/api/admin/books";

	private static final String ADMIN = TestJwt.admin(SUBJECT);

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private TopicExchange eventsExchange;

	@Autowired
	private FaultInjectingPublisher publisher;

	@Autowired
	private PublisherRepository publisherRepository;

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private JsonMapper jsonMapper;

	private final List<String> queues = new ArrayList<>();

	private Publisher bookPublisher;

	private Author author;

	private Category category;

	@BeforeEach
	void setUp() {
		publisher.reset();
		bookPublisher = publisherRepository.save(new Publisher("Deniz Yayınları", "deniz-yayinlari"));
		author = authorRepository.save(new Author("Ahmet Yazar", "ahmet-yazar"));
		category = categoryRepository.save(new Category(null, "Roman", "roman"));
	}

	@AfterEach
	void deleteTemporaryQueues() {
		publisher.reset();
		queues.forEach(amqpAdmin::deleteQueue);
	}

	@Test
	void publishedBookEmitsBookUpsertedWithContractPropertiesAndRowMarkedPublished() throws Exception {
		String queue = bindTemporaryQueue("book.#");
		UUID bookId = createPublishedBook();
		String outboxId = jdbc.queryForObject(
				"SELECT BIN_TO_UUID(id) FROM outbox WHERE aggregate_id = UUID_TO_BIN(?) AND event_type = 'BookUpserted'",
				String.class, bookId.toString());
		String payload = jdbc.queryForObject("SELECT payload FROM outbox WHERE id = UUID_TO_BIN(?)", String.class,
				outboxId);
		LocalDateTime createdAt = jdbc.queryForObject("SELECT created_at FROM outbox WHERE id = UUID_TO_BIN(?)",
				LocalDateTime.class, outboxId);

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(messageCount(queue)).isEqualTo(1));
		Message message = rabbitTemplate.receive(queue);

		assertThat(message).isNotNull();
		MessageProperties properties = message.getMessageProperties();
		assertThat(properties.getMessageId()).isEqualTo(outboxId);
		assertThat(properties.getType()).isEqualTo("BookUpserted");
		assertThat(properties.getContentType()).isEqualTo("application/json");
		assertThat(properties.getContentEncoding()).isEqualTo("UTF-8");
		assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
		assertThat(properties.getReceivedRoutingKey()).isEqualTo("book.upserted");
		assertThat(properties.getTimestamp().toInstant())
			.isEqualTo(createdAt.toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
		assertThat(properties.<Object>getHeader(OutboxPublisher.AGGREGATE_TYPE_HEADER)).hasToString("book");
		assertThat(properties.<Object>getHeader(OutboxPublisher.AGGREGATE_ID_HEADER)).hasToString(bookId.toString());
		assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(payload);

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(publishedAt(outboxId)).isNotNull());
		assertThat(rabbitTemplate.receive(queue)).isNull();
	}

	@Test
	void archiveEmitsBookRemovedOnBookRemovedRoutingKey() throws Exception {
		String removedOnly = bindTemporaryQueue("book.removed");
		UUID bookId = createPublishedBook();
		mockMvc.perform(delete(BASE + "/" + bookId).with(bearer(ADMIN))).andExpect(status().isNoContent());
		String removedId = jdbc.queryForObject(
				"SELECT BIN_TO_UUID(id) FROM outbox WHERE aggregate_id = UUID_TO_BIN(?) AND event_type = 'BookRemoved'",
				String.class, bookId.toString());

		await().atMost(TIMEOUT)
			.untilAsserted(() -> assertThat(jdbc.queryForObject(
					"SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class)).isZero());
		assertThat(messageCount(removedOnly)).isEqualTo(1);
		Message message = rabbitTemplate.receive(removedOnly);

		MessageProperties properties = message.getMessageProperties();
		assertThat(properties.getMessageId()).isEqualTo(removedId);
		assertThat(properties.getType()).isEqualTo("BookRemoved");
		assertThat(properties.getReceivedRoutingKey()).isEqualTo("book.removed");
		assertThat(properties.<Object>getHeader(OutboxPublisher.AGGREGATE_ID_HEADER)).hasToString(bookId.toString());
	}

	@Test
	void publishPatchAndArchiveOfTheSameBookArriveInOrder() throws Exception {
		String queue = bindTemporaryQueue("book.#");
		UUID bookId = createPublishedBook();
		long version = jdbc.queryForObject("SELECT version FROM books WHERE id = UUID_TO_BIN(?)", Long.class,
				bookId.toString());
		mockMvc.perform(json(patch(BASE + "/" + bookId), Map.of("version", version, "title", "Yeni Başlık")))
			.andExpect(status().isOk());
		mockMvc.perform(delete(BASE + "/" + bookId).with(bearer(ADMIN))).andExpect(status().isNoContent());
		List<String> outboxIds = jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM outbox ORDER BY created_at, id",
				String.class);
		assertThat(outboxIds).hasSize(3);

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(messageCount(queue)).isEqualTo(3));
		List<String> receivedIds = new ArrayList<>();
		List<String> receivedTypes = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			MessageProperties properties = rabbitTemplate.receive(queue).getMessageProperties();
			receivedIds.add(properties.getMessageId());
			receivedTypes.add(properties.getType());
		}
		assertThat(receivedIds).containsExactlyElementsOf(outboxIds);
		assertThat(receivedTypes).containsExactly("BookUpserted", "BookUpserted", "BookRemoved");
	}

	@Test
	void failureStopsTheBatchKeepingOrderAndCommitsEarlierRows() {
		String queue = bindTemporaryQueue("book.#");
		List<UUID> ids = OutboxTestRows.newIds(3);
		// Hata INSERT'ten önce kurulur; worker satırları eklendiği anda görebilir.
		publisher.failOn(ids.get(1)::equals);
		OutboxTestRows.insert(jdbc, ids);
		String first = ids.get(0).toString();
		String second = ids.get(1).toString();
		String third = ids.get(2).toString();

		await().atMost(TIMEOUT).until(() -> publisher.attemptsFor(second) >= 2);
		assertThat(publishedAt(first)).isNotNull();
		assertThat(publishedAt(second)).isNull();
		assertThat(publishedAt(third)).isNull();
		assertThat(publisher.attemptsFor(third)).isZero();

		publisher.failOn(id -> false);

		await().atMost(TIMEOUT).untilAsserted(() -> {
			assertThat(publishedAt(second)).isNotNull();
			assertThat(publishedAt(third)).isNotNull();
		});
		List<String> received = new ArrayList<>();
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(messageCount(queue)).isEqualTo(3));
		for (int i = 0; i < 3; i++) {
			received.add(rabbitTemplate.receive(queue).getMessageProperties().getMessageId());
		}
		assertThat(received).containsExactly(first, second, third);
	}

	private String bindTemporaryQueue(String pattern) {
		Queue temporary = new AnonymousQueue();
		String name = amqpAdmin.declareQueue(temporary);
		queues.add(name);
		amqpAdmin.declareBinding(BindingBuilder.bind(temporary).to(eventsExchange).with(pattern));
		return name;
	}

	private UUID createPublishedBook() throws Exception {
		Map<String, Object> body = Map.of("title", "Kırmızı Pazartesi", "publisherId", bookPublisher.getId(),
				"priceAmount", new BigDecimal("149.90"), "initialStock", 5, "authorIds", List.of(author.getId()),
				"categoryIds", List.of(category.getId()));
		MvcResult result = mockMvc.perform(json(post(BASE), body)).andExpect(status().isCreated()).andReturn();
		UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
		mockMvc.perform(post(BASE + "/" + id + "/publish").with(bearer(ADMIN))).andExpect(status().isOk());
		return id;
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) {
		return request.with(bearer(ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content(jsonMapper.writeValueAsString(body));
	}

	private long messageCount(String queue) {
		QueueInformation info = amqpAdmin.getQueueInfo(queue);
		return (info == null) ? -1 : info.getMessageCount();
	}

	private Object publishedAt(String outboxId) {
		return jdbc.queryForObject("SELECT published_at FROM outbox WHERE id = UUID_TO_BIN(?)", Object.class,
				outboxId);
	}

}
