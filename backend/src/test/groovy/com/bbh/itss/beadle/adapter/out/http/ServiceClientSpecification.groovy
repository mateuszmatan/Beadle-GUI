package com.bbh.itss.beadle.adapter.out.http

import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort
import com.bbh.itss.beadle.config.ServicesProperties
import com.bbh.itss.beadle.support.RecordingHttpServer
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

import static org.springframework.context.annotation.FilterType.REGEX

/**
 * Starts the REST clients of Beadle-Internal and Beadle-ProTech with the HTTP message converters of the application
 * against a {@link RecordingHttpServer} that stands for both services.
 */
abstract class ServiceClientSpecification extends Specification {

    static final String CLIENTS =
            '.*\\.(CallerHeaders|InternalClient|InternalJiraAdapter|InternalCyberTrackAdapter|ProTechClient|RestProTechAdapter)'

    static final String TOKEN = 'spec-service-token'
    static final String USER = 'Mateusz Matan'
    /** {@link #USER} as {@code X-Beadle-User} carries it, percent-encoded in UTF-8. */
    static final String USER_HEADER = 'Mateusz+Matan'

    /** The name the signed-in user of the clients has; a feature may change it. */
    static volatile String signedInName = USER

    RecordingHttpServer service = new RecordingHttpServer()
    MovableClock clock = new MovableClock()
    ConfigurableApplicationContext context

    def setup() {
        signedInName = USER
        context = start(service.url)
    }

    def cleanup() {
        context?.close()
        service.close()
    }

    ConfigurableApplicationContext start(String url) {
        new SpringApplicationBuilder(Clients).web(WebApplicationType.NONE).logStartupInfo(false)
                .initializers({ it.beanFactory.registerSingleton('clock', clock) })
                .run("--beadle.services.token=$TOKEN", "--beadle.services.internal.url=$url",
                        "--beadle.services.protech.url=$url/", '--spring.main.banner-mode=off')
    }

    def <T> T bean(Class<T> type) {
        context.getBean(type)
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration([JacksonAutoConfiguration, HttpMessageConvertersAutoConfiguration,
            RestClientAutoConfiguration])
    @EnableConfigurationProperties(ServicesProperties)
    @ComponentScan(basePackages = ['com.bbh.itss.beadle.adapter.out.http', 'com.bbh.itss.beadle.adapter.out.internal',
            'com.bbh.itss.beadle.adapter.out.protech'], useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = REGEX, pattern = CLIENTS))
    static class Clients {

        @Bean
        SignedInUserPort signedInUser() {
            { -> signedInName } as SignedInUserPort
        }
    }

    static class MovableClock extends Clock {

        Instant now = Instant.parse('2026-10-15T09:00:00Z')

        void advance(Duration duration) {
            now = now + duration
        }

        @Override
        ZoneId getZone() {
            ZoneOffset.UTC
        }

        @Override
        Clock withZone(ZoneId zone) {
            this
        }

        @Override
        Instant instant() {
            now
        }
    }
}
