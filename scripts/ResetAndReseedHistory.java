import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ResetAndReseedHistory {
    private static final int WEEKS = 52;

    private record Mapping(long marketplaceProductId, long productId, String marketplace, BigDecimal anchorPrice) {
    }

    public static void main(String[] args) throws Exception {
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");
        if (isBlank(dbUrl) || isBlank(dbUser)) {
            throw new IllegalStateException("DB_URL and DB_USERNAME must be set.");
        }

        try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            connection.setAutoCommit(false);
            List<Mapping> mappings = loadMappings(connection);
            if (mappings.isEmpty()) {
                System.out.println("No mapping found from current history. Nothing to reseed.");
                return;
            }

            int deleted = deleteAllHistory(connection);
            int inserted = 0;
            for (Mapping mapping : mappings) {
                inserted += seedOneYear(connection, mapping);
            }
            connection.commit();
            System.out.println("Deleted history rows: " + deleted);
            System.out.println("Inserted history rows: " + inserted);
            System.out.println("Mapped marketplace products: " + mappings.size());
        }
    }

    private static List<Mapping> loadMappings(Connection connection) throws SQLException {
        String sql = """
                select distinct on (ph.marketplace_product_id)
                       ph.marketplace_product_id,
                       ph.product_id,
                       ph.marketplace,
                       ph.price
                from price_history ph
                order by ph.marketplace_product_id, ph.recorded_at desc
                """;
        List<Mapping> list = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                list.add(new Mapping(
                        rs.getLong("marketplace_product_id"),
                        rs.getLong("product_id"),
                        rs.getString("marketplace"),
                        rs.getBigDecimal("price")
                ));
            }
        }
        return list;
    }

    private static int deleteAllHistory(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("delete from price_history")) {
            return statement.executeUpdate();
        }
    }

    private static int seedOneYear(Connection connection, Mapping mapping) throws SQLException {
        int inserted = 0;
        BigDecimal anchor = mapping.anchorPrice().max(new BigDecimal("5.00"));
        int phase = (int) (mapping.marketplaceProductId() % 17);
        int minuteOffset = (int) (mapping.marketplaceProductId() % 47);

        for (int i = WEEKS; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusWeeks(i);
            LocalDateTime recordedAt = day.atTime(10, minuteOffset);
            BigDecimal multiplier = resolveJaggedMultiplier(mapping.marketplaceProductId(), i, phase);
            BigDecimal price = anchor.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
            insertHistory(
                    connection,
                    mapping.marketplace(),
                    mapping.productId(),
                    mapping.marketplaceProductId(),
                    price.max(new BigDecimal("1.00")),
                    recordedAt
            );
            inserted++;
        }
        return inserted;
    }

    private static BigDecimal resolveJaggedMultiplier(long marketplaceProductId, int weekIndex, int phase) {
        double trend = 1.0
                + (Math.sin((weekIndex + phase) / 8.0) * 0.06)
                + (Math.cos((weekIndex + phase) / 11.0) * 0.03);
        int hashA = Math.floorMod((int) (marketplaceProductId * 37 + weekIndex * 19 + phase * 11), 7);
        int hashB = Math.floorMod((int) (marketplaceProductId * 29 + weekIndex * 13 + phase * 7), 9);
        double zigzagAmplitude = 0.05 + (hashA / 100.0); // 0.05 .. 0.11
        double zigzag = (weekIndex % 2 == 0 ? 1.0 : -1.0) * zigzagAmplitude;
        double noise = (hashB - 4) / 150.0; // -0.026 .. +0.026
        double multiplier = trend + zigzag + noise;

        // Strong opportunity windows with up to 30% drop.
        if (weekIndex % 13 == phase % 13) {
            multiplier *= 0.70;
        } else if (weekIndex % 9 == phase % 9) {
            multiplier *= 0.78;
        } else if (weekIndex % 6 == phase % 6) {
            multiplier *= 0.86;
        }

        // Occasional expensive weeks.
        if (weekIndex % 10 == (phase + 3) % 10) {
            multiplier *= 1.12;
        }

        double bounded = Math.max(0.68, Math.min(multiplier, 1.35));
        return BigDecimal.valueOf(bounded);
    }

    private static void insertHistory(
            Connection connection,
            String marketplace,
            long productId,
            long marketplaceProductId,
            BigDecimal price,
            LocalDateTime recordedAt
    ) throws SQLException {
        String sql = """
                insert into price_history(marketplace, product_id, marketplace_product_id, price, recorded_at)
                values (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, marketplace);
            statement.setLong(2, productId);
            statement.setLong(3, marketplaceProductId);
            statement.setBigDecimal(4, price);
            statement.setTimestamp(5, Timestamp.valueOf(recordedAt));
            statement.executeUpdate();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
