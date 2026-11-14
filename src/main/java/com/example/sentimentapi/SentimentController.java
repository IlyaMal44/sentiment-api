package com.example.sentimentapi;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class SentimentController {

	private final SentimentAnalyzer analyzer;

	public SentimentController(SentimentAnalyzer analyzer) {
		this.analyzer = analyzer;
	}

	@GetMapping("/api/sentiment")
	public SentimentResponse sentiment(@RequestParam String text) {
		if (text.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text must not be blank");
		}
		return new SentimentResponse(analyzer.analyze(text));
	}

}
