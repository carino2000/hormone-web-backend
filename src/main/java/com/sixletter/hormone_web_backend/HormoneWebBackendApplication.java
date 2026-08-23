package com.sixletter.hormone_web_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan  // app.model.* / app.demo.* 설정 바인딩
public class HormoneWebBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(HormoneWebBackendApplication.class, args);
	}

}
