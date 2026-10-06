--liquibase formatted sql

--changeset dso-portal:007-library-config-function dbms:oracle splitStatements:false runOnChange:true
CREATE OR REPLACE FUNCTION DSO_LIBRARY_CONFIG(p_key VARCHAR2) RETURN CLOB AUTHID DEFINER AS
    l_document CLOB;
BEGIN
    SELECT JSON_OBJECT(
               'keyStatus' VALUE k.STATUS,
               'revokeReason' VALUE k.REVOKE_REASON,
               'renderedAt' VALUE TO_CHAR(c.RENDERED_AT, 'YYYY-MM-DD"T"HH24:MI:SS.FF3"Z"'),
               'config' VALUE c.CONFIG_JSON FORMAT JSON
               ABSENT ON NULL RETURNING CLOB)
      INTO l_document
      FROM DSO_PIPELINE_KEY k
      LEFT JOIN DSO_PIPELINE_CONFIG c ON c.PIPELINE_ID = k.PIPELINE_ID AND k.STATUS = 'ACTIVE'
     WHERE k.KEY_VALUE = LOWER(TRIM(p_key));
    RETURN l_document;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RETURN NULL;
END;
--rollback DROP FUNCTION DSO_LIBRARY_CONFIG;
