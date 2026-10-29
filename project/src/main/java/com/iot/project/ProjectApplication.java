package com.iot.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class ProjectApplication {

	public static void main(String[] args) {
		// LocalDateTime.now()가 호스트·컨테이너 시간대(컨테이너 기본 UTC)와 무관하게 KST가 되도록
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(ProjectApplication.class, args);
	}

}
