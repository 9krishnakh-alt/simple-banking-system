import java.io.File;
import java.sql.*;

public class Database {
    private static final String DB_PATH =
        System.getenv().getOrDefault("DB_PATH", "data/banking.db");
    private static final String DB_URL = "jdbc:sqlite:" + DB_PATH;

    private Database() {}

    public static Connection getConnection() throws SQLException {
        File file = new File(DB_PATH);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        return DriverManager.getConnection(DB_URL);
    }

    public static void initialize() throws SQLException {
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS customers(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL,email TEXT NOT NULL)");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS accounts(" +
                "account_number INTEGER PRIMARY KEY," +
                "customer_id INTEGER NOT NULL,balance REAL NOT NULL," +
                "created_at TEXT NOT NULL," +
                "FOREIGN KEY(customer_id) REFERENCES customers(id))");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS transactions(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "account_number INTEGER NOT NULL,type TEXT NOT NULL," +
                "amount REAL NOT NULL,date TEXT NOT NULL," +
                "FOREIGN KEY(account_number) REFERENCES accounts(account_number))");
        }
    }

    public static int count(String table) throws SQLException {
        if (!table.equals("customers") && !table.equals("accounts")
                && !table.equals("transactions")) {
            throw new IllegalArgumentException("Invalid table");
        }
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return r.next() ? r.getInt(1) : 0;
        }
    }

    public static double totalBalance() throws SQLException {
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery(
                 "SELECT COALESCE(SUM(balance),0) FROM accounts")) {
            return r.next() ? r.getDouble(1) : 0;
        }
    }
}
