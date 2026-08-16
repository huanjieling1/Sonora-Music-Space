# MySQL migration package

Database engine: MySQL 8.x

Apply every file in `migrations/` in ascending version order. These files are
exact copies of the Flyway migrations in
`src/main/resources/db/migration` at publication time.

This package does not contain a dump of the developer database. That omission
is deliberate: local dumps can contain personal account, chat, verification,
playlist, and listening-history data.
