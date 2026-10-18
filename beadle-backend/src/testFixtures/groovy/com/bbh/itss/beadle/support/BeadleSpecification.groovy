package com.bbh.itss.beadle.support

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

import static java.lang.System.nanoTime
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

/**
 * The application on a random port and an in-memory H2, calling the {@link StandInServices} for Beadle-Internal and
 * Beadle-ProTech. Every suite spec extends it unchanged, so a suite starts Spring once.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = [
        'spring.datasource.url=jdbc:h2:mem:beadle;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000',
        'beadle.demo-data=false',
        'beadle.services.token=' + BeadleSpecification.SERVICE_TOKEN,
        'beadle.services.internal.url=${beadle.stand-in.url}',
        'beadle.services.protech.url=${beadle.stand-in.url}'])
abstract class BeadleSpecification extends Specification {

    static final String SERVICE_TOKEN = 'regression-service-token'
    static final StandInServices SERVICES = StandInServices.SHARED

    private static final AtomicInteger SEQUENCE = new AtomicInteger()

    @Value('${local.server.port}')
    int port

    @Autowired
    JdbcTemplate jdbc

    BeadleClient getApi() {
        new BeadleClient("http://localhost:$port")
    }

    static String uniqueCode(String prefix = 'REG') {
        "${prefix}${SEQUENCE.incrementAndGet()}${nanoTime() % 100_000}"
    }

    static Map product(Map overrides = [:]) {
        [code: 'CERT', name: 'CertScanner', businessServiceId: 3, ownerTeam: 'Technology Architecture',
         contactEmail: 'ta-team@bbh.com'] + overrides
    }

    Map createProduct(Map product) {
        def response = api.post('/api/products', product)
        assert response.status == 201: response
        response.json as Map
    }
}
