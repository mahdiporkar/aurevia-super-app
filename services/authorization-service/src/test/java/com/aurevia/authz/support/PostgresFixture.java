package com.aurevia.authz.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Executes the real PostgreSQL migrations in a disposable schema, never the public schema. */
public final class PostgresFixture implements AutoCloseable {
  private final String schema="projection_test_"+UUID.randomUUID().toString().replace("-","");
  private final DriverManagerDataSource source;
  private final Connection connection;
  private final JdbcClient database;

  private PostgresFixture(String target) throws SQLException {
    String url=System.getenv("AUREVIA_TEST_JDBC_URL");
    if(url==null||url.isBlank())throw new IllegalStateException("AUREVIA_TEST_JDBC_URL is required");
    source=new DriverManagerDataSource(url,env("AUREVIA_TEST_JDBC_USER","postgres"),
        env("AUREVIA_TEST_JDBC_PASSWORD","postgres"));
    migrateTo(target);
    connection=source.getConnection();
    connection.setSchema(schema);
    database=JdbcClient.create(new SingleConnectionDataSource(connection,true));
  }

  public static PostgresFixture migrated() throws SQLException { return new PostgresFixture("latest"); }
  public static PostgresFixture migratedTo(String version) throws SQLException { return new PostgresFixture(version); }
  public JdbcClient database() { return database; }
  public void migrate() { migrateTo("latest"); }

  private void migrateTo(String target) {
    Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
        .locations("classpath:db/migration").target(target).load().migrate();
  }

  @Override public void close() throws SQLException {
    connection.close();
    try(Connection cleanup=source.getConnection();var statement=cleanup.createStatement()) {
      statement.execute("drop schema "+schema+" cascade");
    }
  }

  private static String env(String key,String fallback) {
    String value=System.getenv(key);return value==null?fallback:value;
  }
}
