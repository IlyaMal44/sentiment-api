package com.example.sentimentapi;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentimentAnalyzerTest {

	@Test
	void assignmentExampleIsPositive() {
		assertThat(SentimentAnalyzer.labelOf("hello")).isEqualTo("positive");
	}

	@Test
	void ignoresCaseAndPunctuation() {
		assertThat(SentimentAnalyzer.labelOf("Hello!")).isEqualTo("positive");
		assertThat(SentimentAnalyzer.labelOf("Это ужасно.")).isEqualTo("negative");
	}

	@Test
	void negativeTokenWinsOverPositive() {
		assertThat(SentimentAnalyzer.labelOf("hello, but terrible")).isEqualTo("negative");
	}

	@Test
	void unknownTextIsNeutral() {
		assertThat(SentimentAnalyzer.labelOf("table")).isEqualTo("neutral");
	}

	@Test
	void analysisCostStaysInteractive() {
		long started = System.nanoTime();
		int cost = SentimentAnalyzer.analysisCost("hello");
		Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
		assertThat(cost).isNotZero();
		assertThat(elapsed).isLessThan(Duration.ofMillis(200));
	}

}
