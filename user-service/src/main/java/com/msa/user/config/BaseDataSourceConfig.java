package com.msa.user.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * master 권한이 있는 baseDataSource 설정파일
 * 테넌트 스키마 생성 시, DDL, DML 권한이 있는 유저를 세팅해줘야함.
 * 따라서 해당 권한이 있는 마스터 유저 세팅
 * 해당 설정파일은 최초 테넌트 스키마 생성에만 사용.
 */
@Configuration
public class BaseDataSourceConfig {
    @Bean(name = "baseDataSource")
    public DataSource baseDataSource(
            @Value("${spring.master-datasource.url}") String url,
            @Value("${spring.master-datasource.username}") String username,
            @Value("${spring.master-datasource.password}") String password
    ) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }
}