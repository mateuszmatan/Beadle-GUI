package com.bbh.itss.beadle.config

import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import spock.lang.Specification

import static org.springframework.boot.WebApplicationType.NONE

class ApplicationProfilesSpec extends Specification {

    static final Map ORACLE = [DB_URL     : 'jdbc:oracle:thin:@//oracle.bbh.test:1521/BEADLE',
                               DB_USERNAME: 'BEADLE', DB_PASSWORD: 'secret']
    static final Map PING = [PING_CLIENT_ID : 'beadle-gui', PING_CLIENT_SECRET: 'not-a-secret',
                             PING_ISSUER_URI: 'https://ping.bbh.test/as']

    def "without a profile Beadle runs locally on port 8081 on H2 in the home folder with demo data"() {
        when:
        def env = environment([], [:])

        then:
        env.activeProfiles as List == []
        env.defaultProfiles as List == ['local']
        env.getProperty('server.port') == '8081'
        env.getProperty('spring.datasource.url') == "jdbc:h2:file:${System.getProperty('user.home')}/beadle/beadle;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;AUTO_SERVER=TRUE"
        environment([], [BEADLE_DATA_DIR: '/var/beadle']).getProperty('spring.datasource.url')
                .startsWith('jdbc:h2:file:/var/beadle/beadle;')
        env.getProperty('spring.jpa.hibernate.ddl-auto') == 'validate'
        env.getProperty('beadle.demo-data') == 'true'
        env.getProperty('management.endpoint.health.show-details') == 'always'
    }

    def "the #profile profile runs on the Oracle database given by the environment"() {
        when:
        def env = environment([profile], ORACLE + variables)

        then:
        env.activeProfiles as List == [profile, 'oracle']
        env.getProperty('spring.datasource.url') == ORACLE.DB_URL
        env.getProperty('spring.datasource.username') == 'BEADLE'
        env.getProperty('spring.datasource.password') == 'secret'
        env.getProperty('spring.jpa.hibernate.ddl-auto') == 'validate'
        env.getProperty('spring.liquibase.analytics-enabled') == 'false'
        env.getProperty('beadle.demo-data') == 'false'
        env.getProperty('spring.datasource.hikari.maximum-pool-size') == poolSize
        env.getProperty('management.endpoint.health.show-details') == healthDetails
        env.getProperty('logging.level.com.bbh.itss.beadle') == logLevel

        where:
        profile | variables            || poolSize | healthDetails | logLevel
        'rd'    | [:]                  || '5'      | 'never'       | 'DEBUG'
        'qc'    | [:]                  || '10'     | 'never'       | 'INFO'
        'prod'  | [:]                  || '20'     | 'never'       | 'INFO'
        'prod'  | [DB_POOL_SIZE: '40'] || '40'     | 'never'       | 'INFO'
    }

    def "the #profile profile does not start without the database address"() {
        when:
        environment([profile], [:]).getProperty('spring.datasource.url')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('DB_URL')

        where:
        profile << ['rd', 'qc', 'prod']
    }

    def "locally Beadle-Internal and Beadle-ProTech answer on ports 8082 and 8083 and no callback is taken without a token"() {
        when:
        def env = environment([], [:])

        then:
        env.getProperty('beadle.services.internal.url') == 'http://localhost:8082'
        env.getProperty('beadle.services.protech.url') == 'http://localhost:8083'
        env.getProperty('beadle.services.token') == ''
        def set = environment([], [BEADLE_INTERNAL_URL: 'http://internal:8082', BEADLE_PROTECH_URL: 'http://protech:8083',
                                   BEADLE_SERVICE_TOKEN: 'shared-secret'])
        ['beadle.services.internal.url', 'beadle.services.protech.url', 'beadle.services.token']
                .collect { set.getProperty(it) } == ['http://internal:8082', 'http://protech:8083', 'shared-secret']
    }

    def "the #profile profile reaches the two services where the environment says"() {
        when:
        def env = environment([profile], ORACLE + [BEADLE_INTERNAL_URL: 'http://beadle-internal:8082',
                                                   BEADLE_PROTECH_URL : 'http://beadle-protech:8083',
                                                   BEADLE_SERVICE_TOKEN: 'shared-secret'])

        then:
        env.getProperty('beadle.services.internal.url') == 'http://beadle-internal:8082'
        env.getProperty('beadle.services.protech.url') == 'http://beadle-protech:8083'
        env.getProperty('beadle.services.token') == 'shared-secret'

        when:
        environment([profile], ORACLE).getProperty("beadle.services.${service}.url")

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(variable)

        where:
        profile | service   | variable
        'rd'    | 'internal' | 'BEADLE_INTERNAL_URL'
        'qc'    | 'protech'  | 'BEADLE_PROTECH_URL'
        'prod'  | 'internal' | 'BEADLE_INTERNAL_URL'
    }

    def "locally everybody is signed in as BEADLE_SIGNED_IN_USER, without Ping"() {
        when:
        def env = environment([], [:])

        then:
        env.getProperty('beadle.auth.mode') == 'local'
        env.getProperty('beadle.signed-in-user') == 'Mateusz Matan'
        env.getProperty('spring.security.oauth2.client.registration.ping.client-id') == null
        environment([], [BEADLE_SIGNED_IN_USER: 'Ann Lee']).getProperty('beadle.signed-in-user') == 'Ann Lee'
        environment([], [BEADLE_AUTH_MODE: 'oidc']).getProperty('beadle.auth.mode') == 'oidc'
    }

    def "the #profile profile signs people in with Ping as the environment says"() {
        when:
        def env = environment([profile], ORACLE + PING)

        then:
        env.getProperty('beadle.auth.mode') == 'oidc'
        ['client-id', 'client-secret', 'authorization-grant-type', 'scope'].collect {
            env.getProperty("spring.security.oauth2.client.registration.ping.$it")
        } == ['beadle-gui', 'not-a-secret', 'authorization_code', 'openid, profile, email']
        env.getProperty('spring.security.oauth2.client.provider.ping.issuer-uri') == PING.PING_ISSUER_URI
        env.getProperty('server.forward-headers-strategy') == 'native'

        when:
        environment([profile], ORACLE + PING.findAll { it.key != variable })
                .getProperty("spring.security.oauth2.client.${property}")

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(variable)

        where:
        profile | variable             | property
        'rd'    | 'PING_CLIENT_ID'     | 'registration.ping.client-id'
        'qc'    | 'PING_CLIENT_SECRET' | 'registration.ping.client-secret'
        'prod'  | 'PING_ISSUER_URI'    | 'provider.ping.issuer-uri'
    }

    private static Environment environment(List<String> profiles, Map<String, String> variables) {
        def application = new SpringApplicationBuilder(Empty)
                .web(NONE)
                .logStartupInfo(false)
                .properties(variables.collectEntries { name, value -> [name, value] } as Map<String, Object>)
        if (profiles) {
            application.profiles(profiles as String[])
        }
        def context = application.run()
        def environment = context.environment
        context.close()
        environment
    }

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }
}
