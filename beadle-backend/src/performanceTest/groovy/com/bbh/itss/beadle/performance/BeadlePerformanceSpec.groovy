package com.bbh.itss.beadle.performance

import com.bbh.itss.beadle.support.BeadleSpecification
import com.bbh.itss.beadle.support.LatencyStats

class BeadlePerformanceSpec extends BeadleSpecification {

    def "products, departments and changes respond with low p95 latencies"() {
        when:
        def productsStats = LatencyStats.measure([calls: 30, threads: 4], 'GET /api/products') {
            api.get('/api/products').status == 200
        }
        def departmentsStats = LatencyStats.measure([calls: 30, threads: 4], 'GET /api/departments') {
            api.get('/api/departments').status == 200
        }
        def changesStats = LatencyStats.measure([calls: 30, threads: 4], 'GET /api/changes') {
            api.get('/api/changes').status == 200
        }

        then:
        productsStats.p95 < 500
        departmentsStats.p95 < 500
        changesStats.p95 < 500
        productsStats.errors == 0
        departmentsStats.errors == 0
        changesStats.errors == 0
    }
}
