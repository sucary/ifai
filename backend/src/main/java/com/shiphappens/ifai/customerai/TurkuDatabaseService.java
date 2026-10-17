package com.shiphappens.ifai.customerai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class TurkuDatabaseService {
    private final Path databasePath;
    private final CustomerAiService customerAiService;

    public TurkuDatabaseService(@Value("${ifai.database.path:data/turku_data.sqlite}") String databasePath, CustomerAiService customerAiService) {
        this.databasePath = Path.of(databasePath);
        this.customerAiService = customerAiService;
    }

    public List<DemoScenario> scenarios() {
        List<DemoScenario> result = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement products = connection.prepareStatement("""
                 SELECT product_id, name, baseline_daily_units, shelf_life_days_on_arrival,
                        selling_price_eur_incl_vat, minimum_order_units, maximum_order_units
                 FROM products ORDER BY product_id LIMIT 5
                 """)) {
            try (ResultSet rs = products.executeQuery()) {
                while (rs.next()) result.add(buildScenario(connection, rs));
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read Turku SQLite data at " + databasePath, error);
        }
        return result;
    }

    private DemoScenario buildScenario(Connection c, ResultSet product) throws SQLException {
        String productId = product.getString("product_id");
        String productName = product.getString("name");
        int baseline = product.getInt("baseline_daily_units");
        double price = product.getDouble("selling_price_eur_incl_vat");
        int minimumOrder = product.getInt("minimum_order_units");
        int maximumOrder = product.getInt("maximum_order_units");

        LatestSale sale = latestSale(c, productId);
        LocalDate forecastDate = LocalDate.parse(sale.date()).plusDays(1);
        int currentStock = latestStock(c, productId, sale.closingUnits());
        int expiringTomorrow = expiringTomorrow(c, productId, forecastDate);
        double weekdayFactor = factor(c, "product_weekday_factors", "weekday", productId, forecastDate.getDayOfWeek().getValue() - 1, 1.0);
        double eventFactor = eventFactor(c, productId, forecastDate);
        int predictedSales = Math.max(0, (int) Math.round(baseline * weekdayFactor * eventFactor));
        int usableStock = Math.max(0, currentStock - expiringTomorrow);
        int ifNeeded = Math.max(0, predictedSales - usableStock);
        int ifRestock = ifNeeded >= minimumOrder ? Math.min(ifNeeded, maximumOrder) : 0;
        String stockoutRisk = usableStock + ifRestock < predictedSales ? "HIGH" : "LOW";
        String expirationRisk = Math.min(currentStock, expiringTomorrow) > 10 ? "HIGH" : "LOW";
        String weather = weather(c, forecastDate);
        String event = event(c, forecastDate);
        List<SalesPoint> salesSeries = salesSeries(c, productId, forecastDate, baseline);

        InventoryInput input = new InventoryInput(productName, sale.salesUnits(), currentStock, expiringTomorrow,
            price, weather, !event.equals("None"), false, minimumOrder, maximumOrder, 1);
        // The customer's AI remains the single source of truth for the order.
        // IF uses this database-derived context to independently check that decision.
        AiDecision decision = customerAiService.analyze(input);
        String scenarioName = expirationRisk.equals("HIGH")
            ? "Expiry risk — " + productName
            : stockoutRisk.equals("HIGH")
                ? "Stockout risk — " + productName
                : "Normal day — " + productName;
        String description = productName + " (" + productId + ") · forecast for " + forecastDate + " from recorded Turku data.";
        return new DemoScenario(scenarioName, description, input, decision, salesSeries);
    }

    private List<SalesPoint> salesSeries(Connection c, String productId, LocalDate forecastStart, int baseline) throws SQLException {
        List<SalesPoint> points = new ArrayList<>();
        try (PreparedStatement q = c.prepareStatement("SELECT date, sales_units FROM history_sales WHERE product_id=? ORDER BY date DESC LIMIT 14")) {
            q.setString(1, productId);
            try (ResultSet rs = q.executeQuery()) {
                while (rs.next()) points.add(0, new SalesPoint(rs.getString("date"), rs.getDouble("sales_units"), "historical"));
            }
        }
        for (int i = 0; i < 14; i++) {
            LocalDate date = forecastStart.plusDays(i);
            double weekdayFactor = factor(c, "product_weekday_factors", "weekday", productId, date.getDayOfWeek().getValue() - 1, 1.0);
            double eventFactor = eventFactor(c, productId, date);
            points.add(new SalesPoint(date.toString(), Math.max(0, Math.round(baseline * weekdayFactor * eventFactor)), "forecast"));
        }
        return points;
    }

    private LatestSale latestSale(Connection c, String productId) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT date, sales_units, closing_units FROM history_sales WHERE product_id=? ORDER BY date DESC LIMIT 1")) {
            q.setString(1, productId);
            try (ResultSet rs = q.executeQuery()) {
                if (!rs.next()) throw new SQLException("No history_sales row for " + productId);
                return new LatestSale(rs.getString("date"), rs.getInt("sales_units"), rs.getInt("closing_units"));
            }
        }
    }

    private int latestStock(Connection c, String productId, int fallback) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT COALESCE(SUM(quantity_remaining), ?) FROM inventory_snapshot WHERE product_id=?")) {
            q.setInt(1, fallback); q.setString(2, productId);
            try (ResultSet rs = q.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private int expiringTomorrow(Connection c, String productId, LocalDate date) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT COALESCE(SUM(quantity_remaining),0) FROM inventory_snapshot WHERE product_id=? AND remove_date=?")) {
            q.setString(1, productId); q.setString(2, date.toString());
            try (ResultSet rs = q.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private double factor(Connection c, String table, String weekdayColumn, String productId, int weekday, double fallback) throws SQLException {
        String sql = "SELECT demand_factor FROM " + table + " WHERE product_id=? AND " + weekdayColumn + "=? LIMIT 1";
        try (PreparedStatement q = c.prepareStatement(sql)) {
            q.setString(1, productId); q.setInt(2, weekday);
            try (ResultSet rs = q.executeQuery()) { return rs.next() ? rs.getDouble(1) : fallback; }
        }
    }

    private double eventFactor(Connection c, String productId, LocalDate date) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT demand_factor FROM product_event_factors WHERE product_id=? AND date=?")) {
            q.setString(1, productId); q.setString(2, date.toString());
            try (ResultSet rs = q.executeQuery()) { return rs.next() ? rs.getDouble(1) : 1.0; }
        }
    }

    private String event(Connection c, LocalDate date) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT COALESCE(event,'') FROM calendar WHERE date=?")) {
            q.setString(1, date.toString());
            try (ResultSet rs = q.executeQuery()) { return rs.next() && !rs.getString(1).isBlank() ? rs.getString(1) : "None"; }
        }
    }

    private String weather(Connection c, LocalDate date) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT temperature_mean_c, precipitation_expected_mm FROM weather_forecast WHERE date=?")) {
            q.setString(1, date.toString());
            try (ResultSet rs = q.executeQuery()) {
                if (rs.next()) return String.format("Forecast %.1f°C · %.1f mm rain", rs.getDouble(1), rs.getDouble(2));
                return "No forecast available";
            }
        }
    }

    private Connection open() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException error) {
            throw new SQLException("SQLite JDBC driver is not available", error);
        }
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    private record LatestSale(String date, int salesUnits, int closingUnits) {}
}
