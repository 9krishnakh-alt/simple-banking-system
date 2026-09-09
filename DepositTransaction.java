import java.sql.SQLException;

public class DepositTransaction extends Transaction {
    public DepositTransaction(double amount) { super(amount); }

    @Override
    public String getType() { return "DEPOSIT"; }

    @Override
    public boolean process(Account account) throws SQLException {
        if (amount <= 0) return false;
        account.changeBalance(amount);
        account.logTransaction(getType(), amount, date);
        return true;
    }
}
