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
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class SeedThirtyDayVolatileHistory {

    private static final int DAYS = 30;
    private static final int MAX_EXTREME_DAYS = 10;

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
            int deleted = 0;
            int inserted = 0;
            int mapped = 0;

            try (PreparedStatement load = connection.prepareStatement("""
                    select distinct on (ph.marketplace_product_id)
                           ph.marketplace_product_id,
                           ph.product_id,
                           ph.marketplace,
                           ph.price
                    from price_history ph
                    where ph.price is not null
                    order by ph.marketplace_product_id, ph.recorded_at desc
                    """);
                 ResultSet rs = load.executeQuery()) {
                while (rs.next()) {
                    Mapping mapping = new Mapping(
                            rs.getLong("marketplace_product_id"),
                            rs.getLong("product_id"),
                            rs.getString("marketplace"),
                            rs.getBigDecimal("price")
                    );
                    if (mapping.anchorPrice() == null || mapping.anchorPrice().compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    mapped++;
                    deleted += deleteWindow(connection, mapping.marketplaceProductId());
                    inserted += seedWindow(connection, mapping);
                }
            }

            connection.commit();
            System.out.println("Mapped marketplace products: " + mapped);
            System.out.println("Deleted rows in 30-day window: " + deleted);
            System.out.println("Inserted rows: " + inserted);
        }
    }

    private static int deleteWindow(Connection connection, long marketplaceProductId) throws SQLException {
        String sql = """
                delete from price_history
                where marketplace_product_id = ?
                  and recorded_at >= ?
                  and recorded_at < ?
                """;
        LocalDate start = LocalDate.now().minusDays(DAYS - 1);
        LocalDate endExclusive = LocalDate.now().plusDays(1);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, marketplaceProductId);
            statement.setTimestamp(2, Timestamp.valueOf(start.atStartOfDay()));
            statement.setTimestamp(3, Timestamp.valueOf(endExclusive.atStartOfDay()));
            return statement.executeUpdate();
        }
    }

    private static int seedWindow(Connection connection, Mapping mapping) throws SQLException {
        int inserted = 0;
        BigDecimal anchor = mapping.anchorPrice().max(new BigDecimal("1.00"));
        Random random = new Random(mapping.marketplaceProductId() * 9973L + 20260227L);
        int extremeDays = random.nextInt(MAX_EXTREME_DAYS + 1);
        Set<Integer> extremeOffsets = pickUniqueOffsets(random, extremeDays, DAYS);
        int minuteOffset = (int) (mapping.marketplaceProductId() % 50);

        String insertSql = """
                insert into price_history(marketplace, product_id, marketplace_product_id, price, recorded_at)
                values (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            for (int daysAgo = DAYS - 1; daysAgo >= 0; daysAgo--) {
                LocalDate day = LocalDate.now().minusDays(daysAgo);
                LocalDateTime recordedAt = day.atTime(10, minuteOffset);
                BigDecimal price;
                if (daysAgo == 0) {
                    price = anchor;
                } else if (extremeOffsets.contains(daysAgo)) {
                    price = scale(anchor, randomBand(random, 0.50, 1.50));
                } else {
                    price = scale(anchor, randomBand(random, 0.70, 1.30));
                }

                statement.setString(1, mapping.marketplace());
                statement.setLong(2, mapping.productId());
                statement.setLong(3, mapping.marketplaceProductId());
                statement.setBigDecimal(4, price.max(new BigDecimal("0.10")));
                statement.setTimestamp(5, Timestamp.valueOf(recordedAt));
                statement.addBatch();
                inserted++;
            }
            statement.executeBatch();
        }
        return inserted;
    }

    private static Set<Integer> pickUniqueOffsets(Random random, int count, int maxDays) {
        Set<Integer> values = new HashSet<>();
        while (values.size() < count) {
            int offset = random.nextInt(maxDays - 1) + 1; // exclude today(0)
            values.add(offset);
        }
        return values;
    }

    private static double randomBand(Random random, double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    private static BigDecimal scale(BigDecimal base, double factor) {
        return base.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
