--liquibase formatted sql

--changeset dso-portal:010-drop-library-config-function dbms:oracle splitStatements:false
DROP FUNCTION DSO_LIBRARY_CONFIG
--rollback CREATE OR REPLACE FUNCTION DSO_LIBRARY_CONFIG(p_key VARCHAR2) RETURN CLOB AUTHID DEFINER AS
--rollback     l_document CLOB;
--rollback BEGIN
--rollback     SELECT JSON_OBJECT(
--rollback                'keyStatus' VALUE k.STATUS,
--rollback                'revokeReason' VALUE k.REVOKE_REASON,
--rollback                'renderedAt' VALUE TO_CHAR(c.RENDERED_AT, 'YYYY-MM-DD"T"HH24:MI:SS.FF3"Z"'),
--rollback                'config' VALUE c.CONFIG_JSON FORMAT JSON
--rollback                ABSENT ON NULL RETURNING CLOB)
--rollback       INTO l_document
--rollback       FROM DSO_PIPELINE_KEY k
--rollback       LEFT JOIN DSO_PIPELINE_CONFIG c ON c.PIPELINE_ID = k.PIPELINE_ID AND k.STATUS = 'ACTIVE'
--rollback      WHERE k.KEY_VALUE = LOWER(TRIM(p_key));
--rollback     RETURN l_document;
--rollback EXCEPTION
--rollback     WHEN NO_DATA_FOUND THEN
--rollback         RETURN NULL;
--rollback END;

--changeset dso-portal:010-drop-published-config dbms:oracle,h2
DROP VIEW DSO_LIBRARY_CONFIG_V;
DROP TABLE DSO_PIPELINE_CONFIG;
--rollback CREATE TABLE DSO_PIPELINE_CONFIG (
--rollback     PIPELINE_ID                 NUMBER(19)      NOT NULL,
--rollback     CONFIG_JSON                 CLOB            NOT NULL,
--rollback     RENDERED_AT                 TIMESTAMP       NOT NULL,
--rollback     CONSTRAINT PK_DSO_PIPELINE_CONFIG PRIMARY KEY (PIPELINE_ID),
--rollback     CONSTRAINT FK_DSO_PIPELINE_CONFIG_PIPELINE FOREIGN KEY (PIPELINE_ID)
--rollback         REFERENCES DSO_PIPELINE (ID) ON DELETE CASCADE
--rollback );
--rollback CREATE VIEW DSO_LIBRARY_CONFIG_V AS
--rollback SELECT k.KEY_VALUE      AS PIPELINE_KEY,
--rollback        k.STATUS         AS KEY_STATUS,
--rollback        k.REVOKE_REASON  AS REVOKE_REASON,
--rollback        c.CONFIG_JSON    AS CONFIG_JSON,
--rollback        c.RENDERED_AT    AS RENDERED_AT
--rollback   FROM DSO_PIPELINE_KEY k
--rollback   LEFT JOIN DSO_PIPELINE_CONFIG c ON c.PIPELINE_ID = k.PIPELINE_ID AND k.STATUS = 'ACTIVE';
