package com.bbh.itss.beadle.adapter.out.persistence

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.Scope
import liquibase.changelog.FastCheckService
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Timestamp

/**
 * The Liquibase changelog on H2 in Oracle mode, without Spring: each feature starts from an empty schema, runs the
 * changesets up to the one it is about, puts data in, runs the rest and rolls back.
 */
class MigrationSpec extends Specification {

    static final List<String> TABLES = ['BEADLE_CHANGE_PROFILE', 'BEADLE_CHANGE_PROFILE_PRIVILEGED_USER',
                                        'BEADLE_CHANGE_PROFILE_TASK', 'BEADLE_DEPARTMENT', 'BEADLE_PRODUCT',
                                        'BEADLE_PRODUCTION_CHANGE', 'BEADLE_PRODUCTION_CHANGE_APPROVAL',
                                        'BEADLE_PRODUCTION_CHANGE_PRIVILEGED_USER', 'BEADLE_PRODUCTION_CHANGE_STAGE',
                                        'BEADLE_PRODUCTION_CHANGE_TASK']
    static final List<String> BUSINESS_SERVICES = ['AI Lab', 'Capital Partners', 'Corporate Technology', 'Custody',
                                                   'Fund Services']

    @Shared
    JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            'jdbc:h2:mem:migration;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1', 'sa', ''))

    Liquibase liquibase

    def setup() {
        System.setProperty('liquibase.analytics.enabled', 'false')
        jdbc.execute('DROP ALL OBJECTS')
        Scope.currentScope.getSingleton(FastCheckService).clearCache()
        liquibase = new Liquibase('db/changelog/db.changelog-master.yaml', new ClassLoaderResourceAccessor(),
                DatabaseFactory.instance.findCorrectDatabaseImplementation(new JdbcConnection(jdbc.dataSource.connection)))
    }

    def cleanup() {
        liquibase.close()
    }

    def "001 creates Beadle's tables with the BBH departments, 002 the audit trail, and both roll back"() {
        when:
        updateBefore('003-')

        then:
        tables() == (TABLES + 'BEADLE_ACTION_HISTORY').sort()
        jdbc.queryForList('SELECT NAME FROM BEADLE_DEPARTMENT ORDER BY ID', String) == BUSINESS_SERVICES
        ['AT', 'USER_NAME', 'ACTION', 'OUTCOME', 'DURATION_MS'].every { !nullable('BEADLE_ACTION_HISTORY', it) }
        ['OBJECT_TYPE', 'OBJECT_ID', 'OBJECT_KEY', 'DETAILS', 'MESSAGE', 'CORRELATION_ID']
                .every { nullable('BEADLE_ACTION_HISTORY', it) }

        when:
        rollBackSince('002-')

        then:
        tables() == TABLES

        when:
        rollBackSince('001-')

        then:
        tables() == []
    }

    def "the audit trail holds known actions, objects and outcomes only: not #action #objectType #outcome"() {
        given:
        liquibase.update('')

        expect:
        audit('CHANGE_CREATED', 'CHANGE', 'SUCCESS') + audit('BUSINESS_SERVICE_CREATED', 'BUSINESS_SERVICE', 'FAILURE') == 2

        when:
        audit(action, objectType, outcome)

        then:
        thrown(DataIntegrityViolationException)

        where:
        action               | objectType         | outcome
        'CHANGE_VIEWED'      | 'CHANGE'           | 'SUCCESS'
        'CHANGE_RAISED'      | 'CHANGE'           | 'SUCCESS'
        'CHANGE_CREATED'     | 'INVOICE'          | 'SUCCESS'
        'CHANGE_CREATED'     | 'CHANGE'           | 'UNKNOWN'
        'DEPARTMENT_CREATED' | 'BUSINESS_SERVICE' | 'SUCCESS'
        'CHANGE_CREATED'     | 'DEPARTMENT'       | 'SUCCESS'
    }

    def "003 turns the departments of an existing database into business services, keeping their data, and rolls back"() {
        given: 'a database that ran 001 and 002 before 003 existed'
        updateBefore('003-')
        insert('BEADLE_PRODUCT', [CODE: 'DOCSENSE', NAME: 'DocSense', DEPARTMENT_ID: 1])
        audit('DEPARTMENT_CREATED', 'DEPARTMENT')

        when:
        updateBefore('004-')

        then:
        !('BEADLE_DEPARTMENT' in tables())
        jdbc.queryForList('SELECT NAME FROM BEADLE_BUSINESS_SERVICE ORDER BY ID', String) == BUSINESS_SERVICES
        jdbc.queryForObject("SELECT BUSINESS_SERVICE_ID FROM BEADLE_PRODUCT WHERE CODE = 'DOCSENSE'", Long) == 1
        columns('BEADLE_PRODUCTION_CHANGE').containsAll(['BUSINESS_SERVICE_ID', 'BUSINESS_SERVICE_NAME'])
        audit('BUSINESS_SERVICE_CREATED', 'BUSINESS_SERVICE') == 1

        when:
        rollBackSince('003-')

        then:
        jdbc.queryForObject("SELECT DEPARTMENT_ID FROM BEADLE_PRODUCT WHERE CODE = 'DOCSENSE'", Long) == 1
        jdbc.queryForObject('SELECT COUNT(*) FROM BEADLE_DEPARTMENT', Integer) == 5
        actions() == ['DEPARTMENT_CREATED/DEPARTMENT'] * 2
    }

    def "004 leaves no department in the template columns or the audit trail, keeping their data, and rolls back"() {
        given: 'a database at 003 with a template, a change and audit rows of either name'
        updateBefore('004-')
        insert('BEADLE_PRODUCT', [CODE: 'DOCSENSE', NAME: 'DocSense', BUSINESS_SERVICE_ID: 3])
        long product = jdbc.queryForObject("SELECT ID FROM BEADLE_PRODUCT WHERE CODE = 'DOCSENSE'", Long)
        insert('BEADLE_CHANGE_PROFILE', [PRODUCT_ID: product, REQUEST_DEPARTMENT: 'Custody'])
        insert('BEADLE_PRODUCTION_CHANGE', [CHANGE_NUMBER: 'CHG0031001', REQUEST_DEPARTMENT: 'Custody',
                                            UPDATE_DEPARTMENT: 'Corporate Technology'])
        audit('DEPARTMENT_CREATED', 'DEPARTMENT')
        audit('DEPARTMENT_DELETED', 'DEPARTMENT')
        audit('BUSINESS_SERVICE_RENAMED', 'BUSINESS_SERVICE')
        audit('CHANGE_RAISED', 'CHANGE')

        when:
        updateBefore('005-')

        then:
        jdbc.queryForObject('SELECT REQUEST_BUSINESS_SERVICE FROM BEADLE_CHANGE_PROFILE', String) == 'Custody'
        jdbc.queryForMap('SELECT REQUEST_BUSINESS_SERVICE, UPDATE_BUSINESS_SERVICE FROM BEADLE_PRODUCTION_CHANGE') ==
                [REQUEST_BUSINESS_SERVICE: 'Custody', UPDATE_BUSINESS_SERVICE: 'Corporate Technology']
        ['BEADLE_CHANGE_PROFILE', 'BEADLE_PRODUCTION_CHANGE'].every { table -> columns(table).every { !it.contains('DEPARTMENT') } }
        actions() == ['BUSINESS_SERVICE_CREATED/BUSINESS_SERVICE', 'BUSINESS_SERVICE_DELETED/BUSINESS_SERVICE',
                      'BUSINESS_SERVICE_RENAMED/BUSINESS_SERVICE', 'CHANGE_RAISED/CHANGE']

        when:
        audit('DEPARTMENT_RENAMED', 'BUSINESS_SERVICE')

        then:
        thrown(DataIntegrityViolationException)

        when:
        rollBackSince('004-')

        then:
        jdbc.queryForObject('SELECT REQUEST_DEPARTMENT FROM BEADLE_CHANGE_PROFILE', String) == 'Custody'
        jdbc.queryForMap('SELECT REQUEST_DEPARTMENT, UPDATE_DEPARTMENT FROM BEADLE_PRODUCTION_CHANGE') ==
                [REQUEST_DEPARTMENT: 'Custody', UPDATE_DEPARTMENT: 'Corporate Technology']
        actions() == ['CHANGE_RAISED/CHANGE', 'DEPARTMENT_CREATED/DEPARTMENT', 'DEPARTMENT_DELETED/DEPARTMENT',
                      'DEPARTMENT_RENAMED/DEPARTMENT']
        audit('BUSINESS_SERVICE_CREATED', 'BUSINESS_SERVICE') + audit('DEPARTMENT_RENAMED', 'DEPARTMENT') == 2
    }

    def "005 lets the audit trail hold sign-outs, and its rollback deletes them"() {
        given:
        updateBefore('005-')

        when:
        audit('USER_SIGNED_OUT', 'USER')

        then:
        thrown(DataIntegrityViolationException)

        when:
        liquibase.update('')

        then:
        audit('USER_SIGNED_IN', 'USER') + audit('USER_SIGNED_OUT', 'USER') == 2

        when:
        rollBackSince('005-')

        then:
        actions() == ['USER_SIGNED_IN/USER']

        when:
        audit('USER_SIGNED_OUT', 'USER')

        then:
        thrown(DataIntegrityViolationException)
    }

    def "006 files the changes created in ProTech as CHANGE_CREATED, keeping their rows, and rolls back"() {
        given: 'a database at 005 with a change raised under the old name'
        updateBefore('006-')
        audit('CHANGE_RAISED', 'CHANGE')

        when:
        liquibase.update('')

        then:
        actions() == ['CHANGE_CREATED/CHANGE']

        when:
        audit('CHANGE_RAISED', 'CHANGE')

        then:
        thrown(DataIntegrityViolationException)

        when:
        rollBackSince('006-')

        then:
        actions() == ['CHANGE_RAISED/CHANGE']
    }

    private void updateBefore(String id) {
        liquibase.update(liquibase.listUnrunChangeSets(new Contexts(), new LabelExpression())
                .findIndexOf { it.id.startsWith(id) }, '')
    }

    private void rollBackSince(String id) {
        liquibase.rollback(jdbc.queryForObject('''SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ORDEREXECUTED >=
                (SELECT MIN(ORDEREXECUTED) FROM DATABASECHANGELOG WHERE ID LIKE ?)''', Integer, id + '%'), '')
        Scope.currentScope.getSingleton(FastCheckService).clearCache()
    }

    private int audit(String action, String objectType, String outcome = 'SUCCESS') {
        jdbc.update('''INSERT INTO BEADLE_ACTION_HISTORY (AT, USER_NAME, ACTION, OBJECT_TYPE, OBJECT_KEY, OUTCOME,
                DURATION_MS) VALUES (CURRENT_TIMESTAMP, 'Mateusz Matan', ?, ?, 'Treasury', ?, 5)''',
                action, objectType, outcome)
    }

    private List<String> actions() {
        jdbc.queryForList("SELECT ACTION || '/' || OBJECT_TYPE FROM BEADLE_ACTION_HISTORY ORDER BY 1", String)
    }

    /** Inserts a row with the given values and a value of its type in every other column that needs one. */
    private void insert(String table, Map<String, Object> values) {
        Map<String, Object> row = jdbc.queryForList('''SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT IS NULL AND IS_IDENTITY = 'NO' ''', table)
                .collectEntries { [it.COLUMN_NAME, it.DATA_TYPE.startsWith('TIMESTAMP') ? new Timestamp(0)
                        : it.DATA_TYPE.contains('CHAR') ? 'X' : 1] } + values
        jdbc.update("INSERT INTO $table (${row.keySet().join(', ')}) VALUES (${row.collect { '?' }.join(', ')})",
                row.values() as Object[])
    }

    private List<String> tables() {
        jdbc.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME LIKE 'BEADLE\\_%'", String)
                .sort()
    }

    private List<String> columns(String table) {
        jdbc.queryForList('SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?', String, table)
    }

    private boolean nullable(String table, String column) {
        jdbc.queryForObject('SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?',
                String, table, column) == 'YES'
    }
}
