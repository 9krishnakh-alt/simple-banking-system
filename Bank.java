import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Bank {
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public int getNextAccountNumber() throws SQLException {
        String sql = "SELECT COALESCE(MAX(account_number),1000)+1 FROM accounts";
        try (Connection c = Database.getConnection();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getInt(1) : 1001;
        }
    }

    public int openAccount(String name, String email, double initial)
            throws SQLException {
        if (name == null || name.isBlank() || email == null ||
            email.isBlank() || initial < 0) return -1;

        int no = getNextAccountNumber();
        String now = LocalDateTime.now().format(FORMATTER);

        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO customers(name,email) VALUES(?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1, name.trim());
                    p.setString(2, email.trim());
                    p.executeUpdate();
                    try (ResultSet r = p.getGeneratedKeys()) {
                        if (!r.next()) throw new SQLException("Could not create customer.");
                        id = r.getInt(1);
                    }
                }

                try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO accounts(account_number,customer_id,balance,created_at) VALUES(?,?,?,?)")) {
                    p.setInt(1, no);
                    p.setInt(2, id);
                    p.setDouble(3, initial);
                    p.setString(4, now);
                    p.executeUpdate();
                }

                if (initial > 0) {
                    try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO transactions(account_number,type,amount,date) VALUES(?,'DEPOSIT',?,?)")) {
                        p.setInt(1, no);
                        p.setDouble(2, initial);
                        p.setString(3, now);
                        p.executeUpdate();
                    }
                }

                c.commit();
                return no;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public Account getAccount(int no) throws SQLException {
        String sql = "SELECT a.account_number,a.balance,c.id,c.name,c.email " +
                     "FROM accounts a JOIN customers c ON a.customer_id=c.id " +
                     "WHERE a.account_number=?";
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, no);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return null;
                Customer customer = new Customer(
                    r.getInt(3), r.getString(4), r.getString(5));
                return new Account(r.getInt(1), customer, r.getDouble(2));
            }
        }
    }

    public boolean deposit(int no, double amount) throws SQLException {
        Account a = getAccount(no);
        return a != null && a.performTransaction(new DepositTransaction(amount));
    }

    public boolean withdraw(int no, double amount) throws SQLException {
        Account a = getAccount(no);
        return a != null && a.performTransaction(new WithdrawTransaction(amount));
    }
}
