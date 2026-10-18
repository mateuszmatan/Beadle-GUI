package com.bbh.itss.beadle.adapter.out.persistence;

import org.hibernate.dialect.H2Dialect;
import org.hibernate.type.SqlTypes;

/**
 * H2 in Oracle mode for profile local, so that ddl-auto validate passes on the changesets written for Oracle: H2
 * reports NUMBER(19), NUMBER(10) and NUMBER(1) as NUMERIC, where Oracle's own validation accepts them as BIGINT,
 * INTEGER and boolean columns by their type name NUMBER.
 */
public class OracleModeH2Dialect extends H2Dialect {

    @Override
    public boolean equivalentTypes(int entityType, int columnType) {
        return SqlTypes.isIntegral(entityType) && columnType == SqlTypes.NUMERIC
                || super.equivalentTypes(entityType, columnType);
    }
}
