#!/bin/sh
set -euo pipefail

psql -v ON_ERROR_STOP=1 \
     -v db_user="$USER_DB_USER" \
     -v db_password="$USER_DB_PASSWORD" \
     -v db_name="$POSTGRES_DB" \
     -v db_schema="$USER_DB_SCHEMA" \
     --username "$POSTGRES_USER" \
     --dbname "$POSTGRES_DB" <<-'EOSQL'

    CREATE USER :"db_user" WITH PASSWORD :'db_password';

    GRANT CONNECT, CREATE ON DATABASE :"db_name" TO :"db_user";

    CREATE SCHEMA IF NOT EXISTS :"db_schema";

    GRANT USAGE, CREATE ON SCHEMA :"db_schema" TO :"db_user";

    ALTER USER :"db_user" SET search_path TO :"db_schema";

    ALTER DEFAULT PRIVILEGES IN SCHEMA :"db_schema"
        GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON TABLES TO :"db_user";

    ALTER DEFAULT PRIVILEGES IN SCHEMA :"db_schema"
        GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO :"db_user";
EOSQL