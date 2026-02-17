import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class SeedDummyData {
    private static final int HISTORY_POINTS = 10;

    public static void main(String[] args) throws Exception {
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");
        if (isBlank(dbUrl) || isBlank(dbUser)) {
            throw new IllegalStateException("DB_URL and DB_USERNAME must be set.");
        }

        try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            connection.setAutoCommit(false);

            long yogurtCategory = findOrCreateCategory(connection, "Yogurt");
            long milkCategory = findOrCreateCategory(connection, "Sut");
            long snackCategory = findOrCreateCategory(connection, "Atistirmalik");

            seedProductWithHistory(
                    connection, yogurtCategory, "Eker Sade Yogurt 750g", "Eker", "g", 750,
                    new BigDecimal("79.90"), new BigDecimal("66.90"), new BigDecimal("84.90"), true
            );
            seedProductWithHistory(
                    connection, yogurtCategory, "Pinar Yogurt 1200g", "Pinar", "g", 1200,
                    new BigDecimal("97.50"), new BigDecimal("82.00"), new BigDecimal("103.00"), false
            );
            seedProductWithHistory(
                    connection, yogurtCategory, "Sek Suzme Yogurt 400g", "Sek", "g", 400,
                    new BigDecimal("56.90"), new BigDecimal("46.50"), new BigDecimal("62.20"), true
            );

            seedProductWithHistory(
                    connection, milkCategory, "Icim Sut 1L", "Icim", "ml", 1000,
                    new BigDecimal("42.90"), new BigDecimal("35.90"), new BigDecimal("48.50"), false
            );
            seedProductWithHistory(
                    connection, milkCategory, "Pinar Protein Sut 500ml", "Pinar", "ml", 500,
                    new BigDecimal("39.90"), new BigDecimal("31.90"), new BigDecimal("43.90"), true
            );

            seedProductWithHistory(
                    connection, snackCategory, "Eti Canga 45g", "Eti", "g", 45,
                    new BigDecimal("21.90"), new BigDecimal("16.90"), new BigDecimal("24.90"), true
            );
            seedProductWithHistory(
                    connection, snackCategory, "Doritos Taco 170g", "Doritos", "g", 170,
                    new BigDecimal("64.90"), new BigDecimal("53.90"), new BigDecimal("71.90"), false
            );
            seedProductWithHistory(
                    connection, snackCategory, "Pringles Original 165g", "Pringles", "g", 165,
                    new BigDecimal("112.90"), new BigDecimal("89.90"), new BigDecimal("124.90"), true
            );

            connection.commit();
            System.out.println("Dummy seed completed successfully.");
        }
    }

    private static void seedProductWithHistory(
            Connection connection,
            long categoryId,
            String productName,
            String brand,
            String unit,
            int unitValue,
            BigDecimal basePrice,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            boolean hasDiscountCycles
    ) throws SQLException {
        long productId = findOrCreateProduct(connection, categoryId, productName, brand, unit, unitValue);
        long ysMarketplaceProductId = findOrCreateMarketplaceProduct(
                connection, "YS", categoryId, "YS-" + stableExternal(productName), brand
        );
        long mgMarketplaceProductId = findOrCreateMarketplaceProduct(
                connection, "MG", categoryId, "MG-" + stableExternal(productName), brand
        );

        List<BigDecimal> ysSeries = buildSeries(basePrice, minPrice, maxPrice, hasDiscountCycles);
        List<BigDecimal> mgSeries = buildSeries(
                basePrice.multiply(new BigDecimal("1.03")).setScale(2, RoundingMode.HALF_UP),
                minPrice.multiply(new BigDecimal("1.02")).setScale(2, RoundingMode.HALF_UP),
                maxPrice.multiply(new BigDecimal("1.05")).setScale(2, RoundingMode.HALF_UP),
                hasDiscountCycles
        );

        for (int i = 0; i < HISTORY_POINTS; i++) {
            LocalDateTime recordedAt = LocalDateTime.now()
                    .minusDays((HISTORY_POINTS - i) * 3L)
                    .truncatedTo(ChronoUnit.MINUTES);
            insertPriceIfMissing(connection, "YS", productId, ysMarketplaceProductId, ysSeries.get(i), recordedAt);
            insertPriceIfMissing(connection, "MG", productId, mgMarketplaceProductId, mgSeries.get(i), recordedAt);
        }
    }

    private static List<BigDecimal> buildSeries(
            BigDecimal base,
            BigDecimal min,
            BigDecimal max,
            boolean hasDiscountCycles
    ) {
        List<BigDecimal> prices = new ArrayList<>();
        BigDecimal[] pattern = hasDiscountCycles
                ? new BigDecimal[]{new BigDecimal("0.98"), new BigDecimal("1.03"), new BigDecimal("0.92"), new BigDecimal("1.01"), new BigDecimal("0.88")}
                : new BigDecimal[]{new BigDecimal("1.00"), new BigDecimal("1.02"), new BigDecimal("1.01"), new BigDecimal("0.99"), new BigDecimal("1.03")};
        for (int i = 0; i < HISTORY_POINTS; i++) {
            BigDecimal value = base.multiply(pattern[i % pattern.length]).setScale(2, RoundingMode.HALF_UP);
            if (value.compareTo(min) < 0) {
                value = min;
            }
            if (value.compareTo(max) > 0) {
                value = max;
            }
            prices.add(value);
        }
        return prices;
    }

    private static long findOrCreateCategory(Connection connection, String name) throws SQLException {
        Long id = queryId(connection, "select id from categories where lower(name) = lower(?)", name);
        if (id != null) {
            return id;
        }
        return insertAndReturnId(connection, "insert into categories(name) values (?)", name);
    }

    private static long findOrCreateProduct(
            Connection connection,
            long categoryId,
            String name,
            String brand,
            String unit,
            int unitValue
    ) throws SQLException {
        Long id = queryId(
                connection,
                "select id from products where category_id = ? and lower(name) = lower(?)",
                categoryId, name
        );
        if (id != null) {
            return id;
        }
        String sql = "insert into products(category_id, name, brand, unit, unit_value, created_at) values (?, ?, ?, ?, ?, ?)";
        return insertAndReturnId(connection, sql, categoryId, name, brand, unit, unitValue, Timestamp.valueOf(LocalDateTime.now()));
    }

    private static long findOrCreateMarketplaceProduct(
            Connection connection,
            String marketplace,
            long categoryId,
            String externalId,
            String brand
    ) throws SQLException {
        Long id = queryId(
                connection,
                "select id from marketplace_products where marketplace = ? and category_id = ? and external_id = ?",
                marketplace, categoryId, externalId
        );
        if (id != null) {
            return id;
        }
        String imageUrl = "https://dummy.smart-pantry.local/" + externalId + ".png";
        String productUrl = "https://dummy.smart-pantry.local/p/" + externalId;
        String sql = "insert into marketplace_products(marketplace, category_id, external_id, product_url, brand_name, image_url, money_price) values (?, ?, ?, ?, ?, ?, ?)";
        return insertAndReturnId(
                connection,
                sql,
                marketplace,
                categoryId,
                externalId,
                productUrl,
                brand,
                imageUrl,
                (Object) null
        );
    }

    private static void insertPriceIfMissing(
            Connection connection,
            String marketplace,
            long productId,
            long marketplaceProductId,
            BigDecimal price,
            LocalDateTime recordedAt
    ) throws SQLException {
        Long existing = queryId(
                connection,
                "select id from price_history where marketplace = ? and product_id = ? and marketplace_product_id = ? and recorded_at = ?",
                marketplace,
                productId,
                marketplaceProductId,
                Timestamp.valueOf(recordedAt)
        );
        if (existing != null) {
            return;
        }
        String sql = "insert into price_history(marketplace, product_id, marketplace_product_id, price, recorded_at) values (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, marketplace);
            statement.setLong(2, productId);
            statement.setLong(3, marketplaceProductId);
            statement.setBigDecimal(4, price);
            statement.setTimestamp(5, Timestamp.valueOf(recordedAt));
            statement.executeUpdate();
        }
    }

    private static Long queryId(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParams(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    private static long insertAndReturnId(Connection connection, String sql, Object... params) throws SQLException {
        String withReturning = sql + " returning id";
        try (PreparedStatement statement = connection.prepareStatement(withReturning)) {
            bindParams(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Insert failed: " + sql);
    }

    private static void bindParams(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            int index = i + 1;
            if (value == null) {
                statement.setObject(index, null);
            } else if (value instanceof Long l) {
                statement.setLong(index, l);
            } else if (value instanceof Integer integer) {
                statement.setInt(index, integer);
            } else if (value instanceof String s) {
                statement.setString(index, s);
            } else if (value instanceof BigDecimal decimal) {
                statement.setBigDecimal(index, decimal);
            } else if (value instanceof Timestamp timestamp) {
                statement.setTimestamp(index, timestamp);
            } else {
                statement.setObject(index, value);
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String stableExternal(String name) {
        return name
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
