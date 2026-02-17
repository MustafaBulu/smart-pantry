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

public class SeedOneYearHistory {
    private static final int WEEKS = 52;

    private record SeedTarget(
            long categoryId,
            String categoryName,
            long marketplaceProductId,
            String marketplaceCode,
            long productId,
            BigDecimal anchorPrice
    ) {
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
            List<SeedTarget> targets = loadTargets(connection);
            if (targets.isEmpty()) {
                System.out.println("No suitable marketplace products found for seeding.");
                return;
            }

            int inserted = 0;
            for (SeedTarget target : targets) {
                inserted += seedWeeklyHistory(connection, target);
            }
            connection.commit();
            System.out.println("Seeded one-year history points: " + inserted);
            System.out.println("Target count: " + targets.size());
        }
    }

    private static List<SeedTarget> loadTargets(Connection connection) throws SQLException {
        String sql = """
                with latest_five_categories as (
                    select c.id, c.name
                    from categories c
                    order by c.id desc
                    limit 5
                ),
                latest_history as (
                    select ph.marketplace_product_id,
                           ph.product_id,
                           ph.price,
                           row_number() over (partition by ph.marketplace_product_id order by ph.recorded_at desc) as rn
                    from price_history ph
                )
                select c.id as category_id,
                       c.name as category_name,
                       mp.id as marketplace_product_id,
                       mp.marketplace,
                       lh.product_id,
                       lh.price
                from marketplace_products mp
                join latest_five_categories c on c.id = mp.category_id
                join latest_history lh on lh.marketplace_product_id = mp.id and lh.rn = 1
                order by c.id, mp.marketplace, mp.id
                """;

        List<SeedTarget> targets = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                targets.add(new SeedTarget(
                        rs.getLong("category_id"),
                        rs.getString("category_name"),
                        rs.getLong("marketplace_product_id"),
                        rs.getString("marketplace"),
                        rs.getLong("product_id"),
                        rs.getBigDecimal("price")
                ));
            }
        }
        return targets;
    }

    private static int seedWeeklyHistory(Connection connection, SeedTarget target) throws SQLException {
        int inserted = 0;
        BigDecimal anchor = target.anchorPrice().max(new BigDecimal("5.00"));
        for (int i = WEEKS; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusWeeks(i);
            LocalDateTime recordedAt = day.atTime(10, 0);
            if (existsRecord(connection, target.marketplaceProductId(), recordedAt)) {
                continue;
            }
            BigDecimal multiplier = resolveMultiplier(i);
            BigDecimal price = anchor.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
            insertHistory(
                    connection,
                    target.marketplaceCode(),
                    target.productId(),
                    target.marketplaceProductId(),
                    price,
                    recordedAt
            );
            inserted++;
        }
        return inserted;
    }

    private static BigDecimal resolveMultiplier(int weekIndex) {
        if (weekIndex % 13 == 0) {
            return new BigDecimal("0.70"); // up to 30% drop opportunity
        }
        if (weekIndex % 9 == 0) {
            return new BigDecimal("0.78");
        }
        if (weekIndex % 6 == 0) {
            return new BigDecimal("0.85");
        }
        int mod = weekIndex % 5;
        return switch (mod) {
            case 0 -> new BigDecimal("0.96");
            case 1 -> new BigDecimal("1.01");
            case 2 -> new BigDecimal("1.04");
            case 3 -> new BigDecimal("0.92");
            default -> new BigDecimal("0.98");
        };
    }

    private static boolean existsRecord(Connection connection, long marketplaceProductId, LocalDateTime recordedAt)
            throws SQLException {
        String sql = "select 1 from price_history where marketplace_product_id = ? and recorded_at = ? limit 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, marketplaceProductId);
            statement.setTimestamp(2, Timestamp.valueOf(recordedAt));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
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
