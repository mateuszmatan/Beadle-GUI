package com.bbh.itss.beadle.performance

import com.bbh.itss.beadle.support.BeadleSpecification
import com.bbh.itss.beadle.support.LatencyStats
import org.springframework.boot.test.context.SpringBootTest

import java.time.Instant

import static com.bbh.itss.beadle.support.ChangeFixtures.scheduleJson
import static java.net.URLEncoder.encode
import static java.nio.charset.StandardCharsets.UTF_8
import static java.time.temporal.ChronoUnit.DAYS
import static java.time.temporal.ChronoUnit.HOURS
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = [
        'spring.datasource.url=jdbc:h2:mem:beadle;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000',
        'beadle.demo-data=true',
        'beadle.services.internal.url=${beadle.stand-in.url}',
        'beadle.services.protech.url=${beadle.stand-in.url}'])
class BeadlePerformanceSpec extends BeadleSpecification {

    /** How much slower than a developer's machine the one running the suite may be ({@code -Dperformance.factor}). */
    static final double FACTOR = (System.getProperty('performance.factor') ?: '1') as double
    static final double P95_LIMIT_MS = 500 * FACTOR
    static final Instant START = Instant.now().plus(3, DAYS).truncatedTo(HOURS)

    def "the key calls answer within their limit at the 95th percentile on the demo catalogue"() {
        given: 'two changes raised for each product of Corporate Technology'
        def requests = api.get('/api/products').json.findAll { it.departmentId == 3 }.collect { changeOf(it) }
        def raised = requests.collectMany { request -> (1..2).collect { api.post('/api/changes', request) } }
        assert raised && raised.every { it.status == 201 }: raised
        def first = raised[0].json.id

        when:
        def stats = [measure('GET /api/changes?departmentId=3') { api.get('/api/changes?departmentId=3') },
                     measure("GET /api/changes/$first") { api.get("/api/changes/$first") },
                     measure('POST /api/changes/preview') { api.post('/api/changes/preview', requests[0]) },
                     measure('GET /api/action-history?size=50') { api.get('/api/action-history?size=50') }]

        then:
        stats.each { assert it.errors == 0 && it.p95 < P95_LIMIT_MS: it }
    }

    private static LatencyStats measure(String name, Closure request) {
        LatencyStats.measure([calls: 20, threads: 4], name) { request().status == 200 }
    }

    private Map changeOf(Map product) {
        def template = api.get("/api/products/$product.id/change-profile").json.template
        def version = api.get("/api/products/$product.id/jira/versions").json[0].name as String
        def epics = api.get("/api/products/$product.id/jira/epics?fixVersion=${encode(version, UTF_8)}").json*.key
        [productId: product.id, fixVersion: version, epicKeys: epics, storyKeys: [], template: template,
         schedule: scheduleJson(START, template.downtime as boolean)]
    }
}
