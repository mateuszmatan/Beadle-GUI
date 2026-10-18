package com.bbh.itss.beadle.support

import com.bbh.itss.beadle.domain.catalog.ProductDetails

class CatalogFixtures {

    static final long BUSINESS_SERVICE_ID = 3L

    private CatalogFixtures() {
    }

    static ProductDetails details(Map args = [:]) {
        new ProductDetails(args.code as String ?: 'CERT', args.name as String ?: 'CertScanner',
                args.description as String, args.ownerTeam as String, args.contactEmail as String,
                args.containsKey('businessServiceId') ? args.businessServiceId as Long : BUSINESS_SERVICE_ID)
    }

    static <T extends Record> T copy(Map changes, T record) {
        def components = record.class.recordComponents
        def args = components.collect { changes.containsKey(it.name) ? changes[it.name] : it.accessor.invoke(record) }
        record.class.declaredConstructors.find { it.parameterCount == components.length }
                .newInstance(args as Object[]) as T
    }
}
