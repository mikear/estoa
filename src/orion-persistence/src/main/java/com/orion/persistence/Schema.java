package com.orion.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Esquema compatible H2 / PostgreSQL. */
final class Schema {

    private Schema() {}

    static final String[] DDL = {
            """
            CREATE TABLE IF NOT EXISTS investigations (
              id VARCHAR(64) PRIMARY KEY,
              name VARCHAR(300) NOT NULL,
              description TEXT,
              status VARCHAR(30),
              priority VARCHAR(20),
              analyst VARCHAR(100),
              classification VARCHAR(50),
              tags TEXT,
              created_at TIMESTAMP,
              updated_at TIMESTAMP,
              graph_json TEXT
            )""",
            """
            CREATE TABLE IF NOT EXISTS ioc_history (
              id VARCHAR(64) PRIMARY KEY,
              investigation_id VARCHAR(64),
              entity_key VARCHAR(300),
              entity_type VARCHAR(40),
              ioc_value VARCHAR(500),
              confidence DOUBLE PRECISION,
              status VARCHAR(20),
              first_seen TIMESTAMP,
              last_seen TIMESTAMP
            )""",
            "CREATE INDEX IF NOT EXISTS idx_ioc_investigation ON ioc_history (investigation_id)",
            "CREATE INDEX IF NOT EXISTS idx_inv_updated ON investigations (updated_at)"
    };

    static void init(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            for (String ddl : DDL) st.execute(ddl);
        }
    }
}
