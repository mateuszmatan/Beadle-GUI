--liquibase formatted sql

--changeset dso-portal:018-product-appscan-key-optional dbms:oracle,h2
ALTER TABLE DSO_PRODUCT MODIFY (ASOC_KEY_ID NULL);
--rollback UPDATE DSO_PRODUCT SET ASOC_KEY_ID = 'Not set' WHERE ASOC_KEY_ID IS NULL;
--rollback ALTER TABLE DSO_PRODUCT MODIFY (ASOC_KEY_ID NOT NULL);
