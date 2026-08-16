# Database source library

This directory is the portable, reviewable database package for the project.
It intentionally contains schema migrations and non-personal seed data only.
Runtime database dumps, user records, conversations, credentials, and local
backups are excluded by the repository `.gitignore`.

The canonical migrations used by Spring Boot/Flyway live in
`src/main/resources/db/migration`. The copies under `mysql/migrations` form a
standalone package that can be reviewed or applied outside the application.

Apply the files in version order (`V1` through the latest migration) to an
empty MySQL 8 database, or start the application and let Flyway apply the
canonical copies automatically.
