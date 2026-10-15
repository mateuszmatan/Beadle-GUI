package com.bbh.itss.beadle.adapter.out.persistence

abstract class BeadleMigrationSpecification extends MigrationSpecification {

    @Override
    protected String changelogFile() {
        'db/changelog/history-with-approvals.yaml'
    }
}
