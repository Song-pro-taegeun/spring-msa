package com.msa.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(
        // 패키지가 현재 com.msa.order / com.msa.tenant로 구성되어 있기에 서비스 시작 시 스캔 범위를 com.msa로 확장한다.
        // 테넌트 설정으로 인해 기본 스캔 범위를 확장해야함.
        scanBasePackages = "com.msa",
        exclude = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class
        }
)
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
        System.out.println("----------------Order Service Start----------------");
    }
}
