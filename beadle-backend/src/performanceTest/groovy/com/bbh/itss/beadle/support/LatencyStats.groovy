package com.bbh.itss.beadle.support

import java.util.concurrent.Callable

import static java.lang.Math.ceil
import static java.lang.System.nanoTime
import static java.util.concurrent.Executors.newFixedThreadPool
import static java.util.concurrent.TimeUnit.SECONDS

class LatencyStats {

    final String name
    final List<Double> millis
    final int errors

    private LatencyStats(String name, List<Double> millis, int errors) {
        this.name = name
        this.millis = millis.sort()
        this.errors = errors
    }

    static LatencyStats measure(Map options, String name, Closure<Boolean> call) {
        int calls = options.calls as int
        int threads = (options.threads ?: 1) as int
        def pool = newFixedThreadPool(threads)
        try {
            if (options.warmUp != false) {
                pool.invokeAll((0..<Math.min(threads, calls)).collect { int index -> { -> call(index) } as Callable })*.get()
            }
            def futures = pool.invokeAll((0..<calls).collect { int index ->
                { ->
                    long begin = nanoTime()
                    boolean ok = call(index)
                    [(nanoTime() - begin) / 1_000_000d, ok]
                } as Callable<List>
            })
            def results = futures*.get()
            new LatencyStats(name, results.collect { it[0] as double }, results.count { !it[1] } as int)
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(10, SECONDS)
        }
    }

    double percentile(double p) {
        millis[Math.min(millis.size() - 1, ceil(p / 100 * millis.size()) as int - 1)]
    }

    double getP95() {
        percentile(95)
    }

    @Override
    String toString() {
        String.format('%s: p95 %.1f ms, %d errors', name, p95, errors)
    }
}
