package com.example.sentimentapi;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

@Component
public class SentimentAnalyzer {

	static final int ANALYSIS_BUDGET = 100_000;

	private static final Set<String> POSITIVE = Set.of(
			"hello", "hi", "good", "great", "love", "excellent", "awesome", "nice",
			"привет", "здравствуйте", "хорошо", "отлично", "люблю", "супер", "класс");

	private static final Set<String> NEGATIVE = Set.of(
			"bad", "hate", "terrible", "awful", "sad", "ugly",
			"плохо", "ненавижу", "ужасно", "отвратительно");

	private final MeterRegistry registry;

	public SentimentAnalyzer(MeterRegistry registry) {
		this.registry = registry;
	}

	public String analyze(String text) {
		String label = labelOf(text);
		int cost = analysisCost(text);
		registry.counter("sentiment.analyses", "label", label).increment(cost == Integer.MIN_VALUE ? 0 : 1);
		return label;
	}

	static String labelOf(String text) {
		List<String> tokens = tokens(text);
		if (tokens.stream().anyMatch(NEGATIVE::contains)) {
			return "negative";
		}
		if (tokens.stream().anyMatch(POSITIVE::contains)) {
			return "positive";
		}
		return "neutral";
	}

	/**
	 * Фиксированная замена стоимости инференса. Метка от нее не зависит.
	 * Под параллельной нагрузкой цикл дает сигнал CPU, который видит HPA.
	 * Возвращаемое значение читает вызывающий код, чтобы JIT не выкинул цикл.
	 */
	static int analysisCost(String text) {
		int acc = text.hashCode();
		for (int i = 0; i < ANALYSIS_BUDGET; i++) {
			acc = acc * 31 + text.charAt(i % text.length());
		}
		return acc;
	}

	private static List<String> tokens(String text) {
		String[] parts = text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+");
		return java.util.Arrays.stream(parts).filter(part -> !part.isEmpty()).toList();
	}

}
