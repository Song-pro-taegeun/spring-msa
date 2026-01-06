package com.msa.tenant.config;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.HashMap;
import java.util.Map;

// 테넌트 순서 ---
// 멀티테넌시 활성화 스위치
// Spring에게 멀티테넌시를 사용한다는 걸 공유하기 위한 설정
@Configuration
@EnableJpaRepositories(
        basePackages = "com.msa",
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager"
)
@EntityScan(basePackages = "com.msa")
public class MultiTenantJpaConfig {

    // 멀티테넌트 커넥션 공급자 등록
    @Bean
    public MultiTenantConnectionProvider<String> multiTenantConnectionProvider() {
        return new MariaDbMultiTenantConnectionProvider();
    }

    // tenant resolver 등록
    @Bean
    public CurrentTenantIdentifierResolver<String> tenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    // EntityManagerFactory에 연결
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            MultiTenantConnectionProvider<String> provider,
            CurrentTenantIdentifierResolver<String> resolver) {

        LocalContainerEntityManagerFactoryBean emf =
                new LocalContainerEntityManagerFactoryBean();

        emf.setPackagesToScan("com.msa");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.multiTenancy", "DATABASE");
        props.put("hibernate.multi_tenant_connection_provider", provider);
        props.put("hibernate.tenant_identifier_resolver", resolver);

        /**
         * 엔티티를 기준으로 테이블을 만들지도 / 수정도 / 검증도 하지마라,
         * 초기 부팅 시 DataSource는 msa_user 스키마에 연결
         * 해당 스키마는 user_service의 마스터 스키마이기에 비즈니스 테이블이 존재하지 않음
         * 해당 설정을 하지 않으면, 부팅 시 missing table 발생
         * 따라서, hibernate에게 DDL에 관여하지 말고, SQL 실행기 역할만 하도록 설정
         */
        props.put("hibernate.hbm2ddl.auto", "none");


        emf.setJpaPropertyMap(props);
        return emf;
    }

    // 트랜잭션 시작 순간에 어느 테넌트를 쓸지 결정된다.
    // 기본적으론 Spring 에서 생성해주지만, LocalContainerEntityManagerFactoryBean 를 직접 정의하여 Spring Boot Auto-config 비활성화
    // Spring Boot Auto-config가 비활성화 되었기에 transactionManager 를 직접 등록해야한다.
    @Bean
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}

