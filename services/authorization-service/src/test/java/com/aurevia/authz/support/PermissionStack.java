package com.aurevia.authz.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Real PostgreSQL + real OpenFGA + real Redis for permission lifecycle tests.
 *
 * <p>Environment: {@code AUREVIA_TEST_JDBC_URL} (a maintenance database the test user may
 * {@code CREATE DATABASE} in), {@code AUREVIA_TEST_JDBC_USER}, {@code AUREVIA_TEST_JDBC_PASSWORD},
 * {@code AUREVIA_TEST_OPENFGA_URL}, {@code AUREVIA_TEST_REDIS_HOST}, {@code AUREVIA_TEST_REDIS_PORT},
 * {@code AUREVIA_TEST_REDIS_PASSWORD}. Every test run gets its own database and OpenFGA store.</p>
 */
public final class PermissionStack {
  public final String jdbcUrl;
  public final String jdbcUser;
  public final String jdbcPassword;
  public final String openFgaUrl;
  public final String storeId;
  public final String modelId;
  public final String redisHost;
  public final int redisPort;
  public final String redisPassword;
  private final String databaseName;
  private final String maintenanceUrl;

  private PermissionStack(String maintenanceUrl, String user, String password, String database,
      String openFgaUrl, String storeId, String modelId) {
    this.maintenanceUrl = maintenanceUrl;
    this.databaseName = database;
    this.jdbcUrl = maintenanceUrl.replaceAll("/[^/?]+(\\?.*)?$", "/" + database);
    this.jdbcUser = user;
    this.jdbcPassword = password;
    this.openFgaUrl = openFgaUrl;
    this.storeId = storeId;
    this.modelId = modelId;
    this.redisHost = env("AUREVIA_TEST_REDIS_HOST", "localhost");
    this.redisPort = Integer.parseInt(env("AUREVIA_TEST_REDIS_PORT", "6379"));
    this.redisPassword = env("AUREVIA_TEST_REDIS_PASSWORD", "");
  }

  public static boolean configured() {
    return !env("AUREVIA_TEST_JDBC_URL", "").isBlank()
        && !env("AUREVIA_TEST_OPENFGA_URL", "").isBlank();
  }

  public static PermissionStack provision() throws Exception {
    String maintenance = env("AUREVIA_TEST_JDBC_URL", "");
    String openFga = env("AUREVIA_TEST_OPENFGA_URL", "");
    if (maintenance.isBlank() || openFga.isBlank()) {
      throw new IllegalStateException(
          "AUREVIA_TEST_JDBC_URL and AUREVIA_TEST_OPENFGA_URL are required");
    }
    String user = env("AUREVIA_TEST_JDBC_USER", "postgres");
    String password = env("AUREVIA_TEST_JDBC_PASSWORD", "postgres");
    String database = "perm_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection connection = DriverManager.getConnection(maintenance, user, password);
        var statement = connection.createStatement()) {
      statement.execute("create database " + database);
    }
    var bootstrap = new OpenFgaClient(new ClientConfiguration().apiUrl(openFga));
    String storeId = bootstrap.createStore(new CreateStoreRequest().name(database)).get().getId();
    bootstrap.setStoreId(storeId);
    ObjectMapper json = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    WriteAuthorizationModelRequest model;
    try (InputStream stream = PermissionStack.class
        .getResourceAsStream("/openfga/authorization-model.json")) {
      model = json.readValue(stream, WriteAuthorizationModelRequest.class);
    }
    String modelId = bootstrap.writeAuthorizationModel(model).get().getAuthorizationModelId();
    return new PermissionStack(maintenance, user, password, database, openFga, storeId, modelId);
  }

  public OpenFgaClient directClient() throws Exception {
    return new OpenFgaClient(new ClientConfiguration().apiUrl(openFgaUrl).storeId(storeId)
        .authorizationModelId(modelId));
  }

  public void dropDatabase() throws Exception {
    try (Connection connection = DriverManager.getConnection(maintenanceUrl, jdbcUser, jdbcPassword);
        var statement = connection.createStatement()) {
      statement.execute("drop database if exists " + databaseName + " with (force)");
    }
  }

  private static String env(String key, String fallback) {
    String value = System.getenv(key);
    return value == null ? fallback : value;
  }

  /**
   * A TCP forwarder in front of OpenFGA that can be switched off to simulate an outage.
   * While paused, new connections are refused and open ones are dropped.
   */
  public static final class PausableProxy implements AutoCloseable {
    private final ServerSocket server;
    private final String targetHost;
    private final int targetPort;
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final List<Socket> open = new CopyOnWriteArrayList<>();
    private final Thread acceptor;

    public PausableProxy(String targetUrl) throws IOException {
      URI target = URI.create(targetUrl);
      this.targetHost = target.getHost();
      this.targetPort = target.getPort() == -1 ? 80 : target.getPort();
      this.server = new ServerSocket(0);
      this.acceptor = new Thread(this::accept, "openfga-proxy-acceptor");
      this.acceptor.setDaemon(true);
      this.acceptor.start();
    }

    public String url() { return "http://127.0.0.1:" + server.getLocalPort(); }

    public void pause() {
      paused.set(true);
      open.forEach(socket -> { try { socket.close(); } catch (IOException ignored) {} });
      open.clear();
    }

    public void resume() { paused.set(false); }

    private void accept() {
      while (!server.isClosed()) {
        try {
          Socket client = server.accept();
          if (paused.get()) { client.close(); continue; }
          Socket upstream = new Socket(targetHost, targetPort);
          open.add(client);
          open.add(upstream);
          pump(client, upstream);
          pump(upstream, client);
        } catch (IOException closed) {
          if (server.isClosed()) return;
        }
      }
    }

    private static void pump(Socket from, Socket to) {
      Thread thread = new Thread(() -> {
        try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
          in.transferTo(out);
        } catch (IOException ignored) {
        } finally {
          try { from.close(); } catch (IOException ignored) {}
          try { to.close(); } catch (IOException ignored) {}
        }
      }, "openfga-proxy-pump");
      thread.setDaemon(true);
      thread.start();
    }

    @Override public void close() throws IOException {
      pause();
      server.close();
    }
  }
}
