package com.example.sentimentapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SentimentApiApplicationTests {

	@LocalServerPort
	private int port;

	@Test
	void helloIsPositive() {
		ResponseEntity<String> response = client().get()
				.uri("/api/sentiment?text=hello")
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo("{\"sentiment\":\"positive\"}");
	}

	@Test
	void terribleIsNegativeAndTableIsNeutral() {
		assertThat(body("/api/sentiment?text=terrible")).isEqualTo("{\"sentiment\":\"negative\"}");
		assertThat(body("/api/sentiment?text=table")).isEqualTo("{\"sentiment\":\"neutral\"}");
		assertThat(body("/api/sentiment?text=" + "отлично")).isEqualTo("{\"sentiment\":\"positive\"}");
	}

	@Test
	void blankTextIsRejected() {
		ResponseEntity<String> response = client().get()
				.uri("/api/sentiment?text=")
				.retrieve()
				.onStatus(status -> true, (request, clientResponse) -> {
				})
				.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void probesAndPrometheusAreExposed() {
		body("/api/sentiment?text=hello");

		assertThat(body("/actuator/health/liveness")).contains("UP");
		assertThat(body("/actuator/health/readiness")).contains("UP");

		String metrics = body("/actuator/prometheus");
		assertThat(metrics).contains("application=\"sentiment-api\"");
		assertThat(metrics).contains("sentiment_analyses_total");
		assertThat(metrics).contains("label=\"positive\"");
	}

	private String body(String path) {
		return client().get().uri(path).retrieve().body(String.class);
	}

	private RestClient client() {
		return RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
	}

}
