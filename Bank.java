import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Bank {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<Customer> customers;
    private final List<Account> accounts;

    public Bank() {
        customers = new ArrayList<>();
        accounts = new ArrayList<>();
    }

    public List<Customer> getCustomers() {
        return customers;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public int getNextAccountNumber() throws SQLException {

        String sql =
            "SELECT COALESCE(MAX(account_number),1000)+1 FROM accounts";

        try (Connection c = Database.getConnection();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(sql)) {

            return r.next() ? r.getInt(1) : 1001;
        }
    }

    public int openAccount(String name, String email, double initial)
            throws SQLException {

        if (name == null || name.isBlank()
                || email == null || email.isBlank()
                || initial < 0) {
            return -1;
        }

        int accountNumber = getNextAccountNumber();

        String now =
            LocalDateTime.now().format(FORMATTER);

        try (Connection c = Database.getConnection()) {

            c.setAutoCommit(false);

            try {

                int customerId;

                // Create Customer in database
                try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO customers(name,email) VALUES(?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {

                    p.setString(1, name.trim());
                    p.setString(2, email.trim());

                    p.executeUpdate();

                    try (ResultSet r = p.getGeneratedKeys()) {

                        if (!r.next()) {
                            throw new SQLException(
                                "Could not create customer."
                            );
                        }

                        customerId = r.getInt(1);
                    }
                }

                // Create Customer object
                Customer customer =
                    new Customer(
                        customerId,
                        name.trim(),
                        email.trim()
                    );

                // Create Account in database
                try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO accounts(" +
                    "account_number,customer_id,balance,created_at)" +
                    " VALUES(?,?,?,?)")) {

                    p.setInt(1, accountNumber);
                    p.setInt(2, customerId);
                    p.setDouble(3, initial);
                    p.setString(4, now);

                    p.executeUpdate();
                }

                // Create Account object
                Account account =
                    new Account(
                        accountNumber,
                        customer,
                        initial
                    );

                // Associate Account with Customer
                customer.addAccount(account);

                // Add objects to Bank collections
                customers.add(customer);
                accounts.add(account);

                // Record initial deposit
                if (initial > 0) {

                    try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO transactions(" +
                        "account_number,type,amount,date)" +
                        " VALUES(?,'DEPOSIT',?,?)")) {

                        p.setInt(1, accountNumber);
                        p.setDouble(2, initial);
                        p.setString(3, now);

                        p.executeUpdate();
                    }
                }

                c.commit();

                return accountNumber;

            } catch (Exception e) {

                c.rollback();

                throw e;
            }
        }
    }

    public Account getAccount(int accountNumber)
            throws SQLException {

        String sql =
            "SELECT a.account_number,a.balance," +
            "c.id,c.name,c.email " +
            "FROM accounts a " +
            "JOIN customers c " +
            "ON a.customer_id=c.id " +
            "WHERE a.account_number=?";

        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {

            p.setInt(1, accountNumber);

            try (ResultSet r = p.executeQuery()) {

                if (!r.next()) {
                    return null;
                }

                Customer customer =
                    new Customer(
                        r.getInt(3),
                        r.getString(4),
                        r.getString(5)
                    );

                Account account =
                    new Account(
                        r.getInt(1),
                        customer,
                        r.getDouble(2)
                    );

                // Associate Account with Customer
                customer.addAccount(account);

                // Keep the objects under Bank's management
                customers.add(customer);
                accounts.add(account);

                return account;
            }
        }
    }

    public boolean deposit(int accountNumber, double amount)
            throws SQLException {

        Account account = getAccount(accountNumber);

        return account != null
                && account.performTransaction(
                    new DepositTransaction(amount)
                );
    }

    public boolean withdraw(int accountNumber, double amount)
            throws SQLException {

        Account account = getAccount(accountNumber);

        return account != null
                && account.performTransaction(
                    new WithdrawTransaction(amount)
                );
    }
            }
