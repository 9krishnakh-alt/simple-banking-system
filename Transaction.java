import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public abstract class Transaction {
    protected final double amount;
    protected final String date;

    protected Transaction(double amount) {
        this.amount = amount;
        this.date = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public double getAmount() { return amount; }
    public String getDate() { return date; }
    public abstract String getType();
    public abstract boolean process(Account account) throws SQLException;
}
