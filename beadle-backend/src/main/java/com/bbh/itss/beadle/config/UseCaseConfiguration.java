package com.bbh.itss.beadle.config;

import com.bbh.itss.beadle.adapter.AuditInterceptor;
import com.bbh.itss.beadle.application.UseCase;
import com.bbh.itss.beadle.application.audit.port.in.ActionHistoryUseCase;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import static org.springframework.aop.support.annotation.AnnotationMatchingPointcut.forClassAnnotation;
import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;
import static org.springframework.context.annotation.FilterType.ANNOTATION;
import static org.springframework.core.Ordered.LOWEST_PRECEDENCE;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = UseCase.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = ANNOTATION, classes = UseCase.class))
public class UseCaseConfiguration {

    /** The transaction advisor wraps the audit advisor, so that an audit row can join the use case's transaction. */
    static final int TRANSACTION_ORDER = LOWEST_PRECEDENCE - 10;
    static final int AUDIT_ORDER = LOWEST_PRECEDENCE - 5;

    @Bean
    @Role(ROLE_INFRASTRUCTURE)
    static Advisor useCaseTransactionAdvisor(BeanFactory beanFactory) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionAttributeSource(new UseCaseTransactionAttributeSource());
        interceptor.setBeanFactory(beanFactory);
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(forClassAnnotation(UseCase.class), interceptor);
        advisor.setOrder(TRANSACTION_ORDER);
        return advisor;
    }

    @Bean
    @Role(ROLE_INFRASTRUCTURE)
    static Advisor useCaseAuditAdvisor(ObjectProvider<ActionHistoryUseCase> history, ObjectProvider<JsonMapper> json,
                                       ObjectProvider<PlatformTransactionManager> transactions,
                                       ObjectProvider<Clock> clock) {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(AuditInterceptor.pointcut(),
                new AuditInterceptor(history, json, transactions, clock));
        advisor.setOrder(AUDIT_ORDER);
        return advisor;
    }
}
