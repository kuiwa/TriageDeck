package com.triagedeck.audit;

import com.triagedeck.platform.audit.Auditable;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 让 {@link Auditable} 注解生效：只代理标注该注解的方法，注入审计 advice。
 *
 * 不新建 AutoProxyCreator：把 advisor 标记为 infrastructure 角色，复用 Spring 事务的
 * InfrastructureAdvisorAutoProxyCreator，使审计与 @Transactional 共用同一个代理，
 * 避免"自建 DefaultAdvisorAutoProxyCreator 抢先代理导致事务失效"的问题。
 */
@Configuration
@EnableTransactionManagement
public class AuditAopConfig {

    @Bean
    public Advisor auditableAdvisor(AuditableAuditAdvice advice) {
        AnnotationMatchingPointcut pointcut =
                new AnnotationMatchingPointcut(null, Auditable.class, true);
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, advice);
        // 置于最外层：事务提交/回滚之后再写审计，避免回滚场景误记 SUCCESS
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return advisor;
    }

    @Bean
    public static BeanFactoryPostProcessor auditableAdvisorInfrastructureRole() {
        return beanFactory -> {
            if (beanFactory instanceof BeanDefinitionRegistry registry
                    && registry.containsBeanDefinition("auditableAdvisor")) {
                BeanDefinition definition = registry.getBeanDefinition("auditableAdvisor");
                definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
            }
        };
    }
}