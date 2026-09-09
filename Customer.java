import java.util.ArrayList;
import java.util.List;

public class Customer {

    private final int id;
    private final String name;
    private final String email;
    private final List<Account> accounts;

    public Customer(int id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.accounts = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void addAccount(Account account) {
        if (account != null) {
            accounts.add(account);
        }
    }
}
