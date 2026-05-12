-- V37: Align finance_predicts_mentions hash columns with the JPA mapping.
--
-- V36 declared text_hash / author_hash as CHAR(64), which Postgres stores as
-- bpchar (Types#CHAR). The PredictsMention entity maps them as plain Strings
-- of length 64, which Hibernate validates as VARCHAR (Types#VARCHAR). On
-- startup with ddl-auto=validate this fails with:
--   Schema validation: wrong column type encountered in column [author_hash]
--   in table [finance_predicts_mentions]; found [bpchar (Types#CHAR)], but
--   expecting [char(64) (Types#VARCHAR)]
--
-- Same defect / same fix shape as V16 → V17 (banking_imports.sha256_hex,
-- dedupe_hash). Hex hashes have no need for blank-padded fixed-width storage.
ALTER TABLE finance_predicts_mentions
    ALTER COLUMN text_hash TYPE VARCHAR(64);

ALTER TABLE finance_predicts_mentions
    ALTER COLUMN author_hash TYPE VARCHAR(64);
