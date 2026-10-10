--liquibase formatted sql

--changeset dso-portal:023-release-gate-state-file dbms:oracle,h2
UPDATE DSO_GLOBAL_SETTINGS SET RELEASE_GATE_STATE_FILE = 'release-gate.json'
 WHERE RELEASE_GATE_STATE_FILE <> 'release-gate.json';
--rollback empty
