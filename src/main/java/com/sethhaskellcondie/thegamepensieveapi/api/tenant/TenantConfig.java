package com.sethhaskellcondie.thegamepensieveapi.api.tenant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registers {@link TenantTransactionFilter} after Spring Security's {@code FilterChainProxy} (which defaults to order
 * {@code -100}), so the {@code SecurityContext} is populated by the time the tenant boundary is established. Only
 * loaded in a servlet web application — the sliced {@code @JdbcTest} repository tests never start it and set the
 * tenant session variable themselves.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TenantConfig {

    @Bean
    public FilterRegistrationBean<TenantTransactionFilter> tenantTransactionFilterRegistration(
            OwnerResolver ownerResolver, PlatformTransactionManager transactionManager, JdbcTemplate jdbcTemplate) {
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        final FilterRegistrationBean<TenantTransactionFilter> registration = new FilterRegistrationBean<>(
                new TenantTransactionFilter(ownerResolver, transactionTemplate, jdbcTemplate));
        registration.addUrlPatterns("/*");
        registration.setOrder(0);
        return registration;
    }
}
