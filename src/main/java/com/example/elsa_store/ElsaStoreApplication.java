package com.example.elsa_store;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class ElsaStoreApplication {

	public static void main(String[] args) {
		// Giờ đặt đơn, "doanh thu hôm nay / tháng này" luôn theo giờ Việt Nam, kể cả khi chạy trên máy chủ đặt UTC.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
		SpringApplication.run(ElsaStoreApplication.class, args);
	}

}
