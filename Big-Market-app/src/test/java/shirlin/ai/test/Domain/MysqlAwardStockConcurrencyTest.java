package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs only against an explicitly configured, dedicated MySQL test database. */
class MysqlAwardStockConcurrencyTest {
    @Test
    void bucketConditionalUpdateNeverOversellsUnderConcurrency() throws Exception {
        String url = System.getenv("BIG_MARKET_STOCK_TEST_JDBC_URL");
        assumeTrue(url != null && url.matches("jdbc:mysql://[^/]+/big_market_stock_test(?:\\?.*)?"),
                "A dedicated big_market_stock_test database is required");
        String user = System.getenv("BIG_MARKET_STOCK_TEST_DB_USER");
        String password = System.getenv("BIG_MARKET_STOCK_TEST_DB_PASSWORD");
        assumeTrue(user != null && password != null, "Dedicated test credentials are required");
        String sql = loadReserveBucketSql();
        String inventoryType = "AWARD";
        String inventoryKey = "test:" + System.currentTimeMillis();
        int bucketId = 0;
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS inventory_stock_bucket ("
                    + "inventory_type VARCHAR(16) NOT NULL, inventory_key VARCHAR(64) NOT NULL,"
                    + "bucket_id SMALLINT NOT NULL, stock_count BIGINT NOT NULL,"
                    + "stock_surplus BIGINT NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY (inventory_type, inventory_key, bucket_id))");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO inventory_stock_bucket "
                            + "(inventory_type, inventory_key, bucket_id, stock_count, stock_surplus) "
                            + "VALUES (?, ?, ?, 10, 10)")) {
                insert.setString(1, inventoryType);
                insert.setString(2, inventoryKey);
                insert.setInt(3, bucketId);
                insert.executeUpdate();
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 100; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try (Connection c = DriverManager.getConnection(url, user, password);
                         PreparedStatement update = c.prepareStatement(sql)) {
                        update.setString(1, inventoryType);
                        update.setString(2, inventoryKey);
                        update.setInt(3, bucketId);
                        return update.executeUpdate();
                    }
                }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Integer> result : results) successes += result.get(30, TimeUnit.SECONDS);
            assertEquals(10, successes);
            try (Connection c = DriverManager.getConnection(url, user, password);
                 PreparedStatement query = c.prepareStatement(
                         "SELECT stock_surplus FROM inventory_stock_bucket "
                                 + "WHERE inventory_type=? AND inventory_key=? AND bucket_id=?")) {
                query.setString(1, inventoryType);
                query.setString(2, inventoryKey);
                query.setInt(3, bucketId);
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    assertEquals(0, rows.getInt(1));
                }
            }
        } finally {
            pool.shutdownNow();
            try (Connection c = DriverManager.getConnection(url, user, password);
                 PreparedStatement delete = c.prepareStatement(
                         "DELETE FROM inventory_stock_bucket "
                                 + "WHERE inventory_type=? AND inventory_key=? AND bucket_id=?")) {
                delete.setString(1, inventoryType);
                delete.setString(2, inventoryKey);
                delete.setInt(3, bucketId);
                delete.executeUpdate();
            }
        }
    }

    private static String loadReserveBucketSql() throws Exception {
        try (InputStream input = MysqlAwardStockConcurrencyTest.class.getResourceAsStream(
                "/mybatis/mapper/InventoryBucketMapper.xml")) {
            if (input == null) throw new IllegalStateException("InventoryBucketMapper.xml is missing");
            String mapper = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher match = Pattern.compile("<update id=\"reserveBucket\">(.*?)</update>", Pattern.DOTALL)
                    .matcher(mapper);
            if (!match.find()) throw new IllegalStateException("reserveBucket SQL is missing");
            return match.group(1).replace("&gt;", ">")
                    .replace("#{inventoryType}", "?")
                    .replace("#{inventoryKey}", "?")
                    .replace("#{bucketId}", "?").trim();
        }
    }
}
