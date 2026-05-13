package br.com.stockshift;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StockshiftApplication {

	public static void main(String[] args) {
		SpringApplication.run(StockshiftApplication.class, args);
	}

}
