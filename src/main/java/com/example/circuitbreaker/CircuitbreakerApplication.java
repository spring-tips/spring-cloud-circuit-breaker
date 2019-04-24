package com.example.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.circuitbreaker.commons.ReactiveCircuitBreaker;
import org.springframework.cloud.circuitbreaker.commons.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@SpringBootApplication
public class CircuitbreakerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CircuitbreakerApplication.class, args);
	}

	@Bean
	ReactiveCircuitBreakerFactory circuitBreakerFactory() {
		var factory = new ReactiveResilience4JCircuitBreakerFactory();
		factory
			.configureDefault(s -> new Resilience4JConfigBuilder(s)
				.timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build())
				.circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
				.build());
		return factory;
	}

}

@RestController
class FailingRestController {

	private final FailingService failingService;
	private final ReactiveCircuitBreaker circuitBreaker;

	FailingRestController(FailingService fs,
																							ReactiveCircuitBreakerFactory cbf) {
		this.failingService = fs;
		this.circuitBreaker = cbf.create("greet");
	}

	@GetMapping("/greet")
	Publisher<String> greet(@RequestParam Optional<String> name) {
		var results = this.failingService.greet(name); // cold
		return this.circuitBreaker.run(results, throwable -> Mono.just("hello world!"));
	}
}

@Log4j2
@Service
class FailingService {

	Mono<String> greet(Optional<String> name) {
		var seconds = (long) (Math.random() * 10);

		return name
			.map(str -> {
				var msg = "Hello " + str + "! (in " + seconds + ")";
				log.info(msg);
				return Mono.just(msg);
			})
			.orElse(Mono.error(new NullPointerException()))
			.delayElement(Duration.ofSeconds(seconds));
	}

}