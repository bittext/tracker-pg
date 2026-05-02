-- Hibernate maps @Column(length=64) on String to VARCHAR; V16 used CHAR(64) (bpchar), which fails ddl-auto=validate.

ALTER TABLE banking_import_files
    ALTER COLUMN sha256_hex TYPE VARCHAR(64);

ALTER TABLE banking_transactions
    ALTER COLUMN dedupe_hash TYPE VARCHAR(64);
