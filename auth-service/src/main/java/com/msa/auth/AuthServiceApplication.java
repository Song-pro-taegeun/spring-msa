package com.msa.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

// JPA 자동설정 끄기
// Spring Boot 기본 DataSource / 기본 EntityManagerFactory 설정 끄기
@SpringBootApplication(
        // 패키지가 현재 com.msa.auth / com.msa.tenant로 구성되어 있기에 서비스 시작 시 스캔 범위를 com.msa로 확장한다.
        // 테넌트 설정으로 인해 기본 스캔 범위를 확장해야함.
        scanBasePackages = "com.msa",
        exclude = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class
        }
)
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
        System.out.println("----------------Auth Service Start----------------");
    }
}
