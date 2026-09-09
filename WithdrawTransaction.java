import java.sql.SQLException;

public class WithdrawTransaction extends Transaction {
    public WithdrawTransaction(double amount) { super(amount); }

    @Override
    public String getType() { return "WITHDRAW"; }

    @Override
    public boolean process(Account account) throws SQLException {
        if (amount <= 0 || amount > account.getBalance()) return false;
        account.changeBalance(-amount);
        account.logTransaction(getType(), amount, date);
        return true;
    }
}
