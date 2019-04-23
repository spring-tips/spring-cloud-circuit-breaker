package com.example.producer;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.circuitbreaker.commons.ReactiveCircuitBreaker;
import org.springframework.cloud.circuitbreaker.commons.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JAutoConfiguration;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Optional;

@SpringBootApplication(exclude = ReactiveResilience4JAutoConfiguration.class)
public class ProducerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProducerApplication.class, args);
	}

	@Bean
	ReactiveResilience4JCircuitBreakerFactory reactiveResilience4JCircuitBreakerFactory() {
		var factory = new ReactiveResilience4JCircuitBreakerFactory();
		factory
			.configureDefault(id -> new Resilience4JConfigBuilder(id)
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

	FailingRestController(ReactiveCircuitBreakerFactory circuitBreakerFactory, FailingService failingService) {
		this.failingService = failingService;
		this.circuitBreaker = circuitBreakerFactory.create("greet");
	}

	@GetMapping("/greet")
	Flux<String> greet(@RequestParam Optional<String> name) {
		var reply = this.failingService.generate(name);
		return this.circuitBreaker.run(reply, throwable -> Flux.just("hello world!"));
	}
}

@Service
@Log4j2
class FailingService {


	Flux<String> generate(Optional<String> name) {
		var seconds = (long) (Math.random() * 10);
		return name
			.map(str -> {
				var msg = "Hello " + name.orElse("") + "!" + " (waiting " + seconds + " seconds)";
				log.info(msg);
				return Flux.just(msg);
			})
			.orElse(Flux.error(new NullPointerException()))
			.delayElements(Duration.ofSeconds(seconds));
	}
}