package com.todo;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Ensures cloud deployment compatibility (Render, Supabase, Railway, Neon, etc.)
 * by automatically converting URI-style database strings (e.g. postgres:// or postgresql://)
 * into standard JDBC connection URLs (jdbc:postgresql://...).
 */
@Configuration
@Profile("!test")
public class DataSourceConfig {
    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${spring.datasource.url:}")
    private String configuredUrl;

    @Value("${spring.datasource.username:}")
    private String configuredUsername;

    @Value("${spring.datasource.password:}")
    private String configuredPassword;

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        String envDbUrl = System.getenv("DATABASE_URL");
        if (envDbUrl == null || envDbUrl.isBlank()) {
            envDbUrl = System.getenv("DB_URL");
        }

        String targetUrl = (envDbUrl != null && !envDbUrl.isBlank()) ? envDbUrl : configuredUrl;
        String username = configuredUsername;
        String password = configuredPassword;

        // Auto-convert postgres:// or postgresql:// to jdbc:postgresql://
        if (targetUrl != null && (targetUrl.startsWith("postgres://") || targetUrl.startsWith("postgresql://"))) {
            try {
                String normalized = targetUrl.startsWith("postgres://")
                        ? "postgresql://" + targetUrl.substring("postgres://".length())
                        : targetUrl;
                URI uri = new URI(normalized);

                if (uri.getUserInfo() != null) {
                    String[] userParts = uri.getUserInfo().split(":", 2);
                    username = URLDecoder.decode(userParts[0], StandardCharsets.UTF_8);
                    if (userParts.length > 1) {
                        password = URLDecoder.decode(userParts[1], StandardCharsets.UTF_8);
                    }
                }

                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String path = uri.getPath() != null && uri.getPath().length() > 1 ? uri.getPath() : "/postgres";
                String query = uri.getQuery() != null ? "?" + uri.getQuery() : "";
                if (host != null && host.contains("supabase.com") && !query.contains("sslmode")) {
                    query = query.isEmpty() ? "?sslmode=require" : query + "&sslmode=require";
                }

                targetUrl = "jdbc:postgresql://" + host + ":" + port + path + query;
                log.info("Detected cloud PostgreSQL URI. Converted to JDBC URL: jdbc:postgresql://{}:{}{}", host, port, path);
            } catch (Exception e) {
                log.warn("Could not parse database URI into components, prepending jdbc: prefix: {}", e.getMessage());
                if (!targetUrl.startsWith("jdbc:")) {
                    targetUrl = "jdbc:" + targetUrl;
                }
            }
        }

        HikariDataSource ds = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        if (targetUrl != null && !targetUrl.isBlank()) {
            ds.setJdbcUrl(targetUrl);
        }
        if (username != null && !username.isBlank()) {
            ds.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            ds.setPassword(password);
        }

        return ds;
    }
}
