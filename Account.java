import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Account {
    private final int accountNumber;
    private final Customer accountHolder;
    private double balance;

    public Account(int accountNumber, Customer accountHolder, double balance) {
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.balance = balance;
    }

    public int getAccountNumber() { return accountNumber; }
    public Customer getAccountHolder() { return accountHolder; }
    public double getBalance() { return balance; }

    public void changeBalance(double amount) throws SQLException {
        balance += amount;
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement(
                 "UPDATE accounts SET balance=? WHERE account_number=?")) {
            p.setDouble(1, balance);
            p.setInt(2, accountNumber);
            p.executeUpdate();
        }
    }

    public void logTransaction(String type, double amount, String date)
            throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement(
                 "INSERT INTO transactions(account_number,type,amount,date) VALUES(?,?,?,?)")) {
            p.setInt(1, accountNumber);
            p.setString(2, type);
            p.setDouble(3, amount);
            p.setString(4, date);
            p.executeUpdate();
        }
    }

    public boolean performTransaction(Transaction transaction)
            throws SQLException {
        return transaction.process(this);
    }
}
