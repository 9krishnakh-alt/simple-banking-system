import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


// =========================
// ABSTRACT TRANSACTION CLASS
// =========================

abstract class Transaction {

    protected String type;
    protected double amount;
    protected LocalDateTime date;

    public Transaction(String type, double amount) {
        this.type = type;
        this.amount = amount;
        this.date = LocalDateTime.now();
    }

    public abstract boolean process(Account account);

    public String getDetails() {

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

        return type + " : Rs. "
                + String.format("%.2f", amount)
                + " | "
                + date.format(formatter);
    }
}


// =========================
// DEPOSIT TRANSACTION
// =========================

class DepositTransaction extends Transaction {

    public DepositTransaction(double amount) {
        super("Deposit", amount);
    }

    @Override
    public boolean process(Account account) {

        if (amount <= 0) {
            return false;
        }

        account.addBalance(amount);
        return true;
    }
}


// =========================
// WITHDRAW TRANSACTION
// =========================

class WithdrawTransaction extends Transaction {

    public WithdrawTransaction(double amount) {
        super("Withdrawal", amount);
    }

    @Override
    public boolean process(Account account) {

        if (amount <= 0) {
            return false;
        }

        if (account.getBalance() >= amount) {

            account.subtractBalance(amount);
            return true;
        }

        return false;
    }
}


// =========================
// ACCOUNT CLASS
// =========================

class Account {

    private String accountNumber;
    private Customer accountHolder;
    private double balance;

    private List<Transaction> transactions;


    public Account(String accountNumber,
                   Customer accountHolder,
                   double initialBalance) {

        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.balance = initialBalance;

        transactions = new ArrayList<>();
    }


    public String getAccountNumber() {
        return accountNumber;
    }


    public Customer getAccountHolder() {
        return accountHolder;
    }


    public double getBalance() {
        return balance;
    }


    public void addBalance(double amount) {
        balance = balance + amount;
    }


    public void subtractBalance(double amount) {
        balance = balance - amount;
    }


    public boolean performTransaction(
            Transaction transaction) {

        if (transaction.process(this)) {

            transactions.add(transaction);

            return true;
        }

        return false;
    }


    public List<Transaction> getTransactions() {
        return transactions;
    }
}


// =========================
// CUSTOMER CLASS
// =========================

class Customer {

    private String id;
    private String name;
    private String email;

    private List<Account> accounts;


    public Customer(String id,
                    String name,
                    String email) {

        this.id = id;
        this.name = name;
        this.email = email;

        accounts = new ArrayList<>();
    }


    public String getId() {
        return id;
    }


    public String getName() {
        return name;
    }


    public String getEmail() {
        return email;
    }


    public void addAccount(Account account) {
        accounts.add(account);
    }


    public List<Account> getAccounts() {
        return accounts;
    }
}


// =========================
// BANK CLASS
// =========================

class Bank {

    private List<Customer> customers;
    private List<Account> accounts;

    private int nextAccountNumber = 1001;


    public Bank() {

        customers = new ArrayList<>();
        accounts = new ArrayList<>();
    }


    // Open a new account

    public Account openAccount(
            String name,
            String email,
            double initialDeposit) {

        String customerId =
                "C" + (customers.size() + 1);


        Customer customer =
                new Customer(
                        customerId,
                        name,
                        email);


        String accountNumber =
                String.valueOf(nextAccountNumber);


        nextAccountNumber++;


        Account account =
                new Account(
                        accountNumber,
                        customer,
                        initialDeposit);


        customer.addAccount(account);

        customers.add(customer);

        accounts.add(account);


        return account;
    }


    // Find account

    public Account getAccount(
            String accountNumber) {

        for (Account account : accounts) {

            if (account.getAccountNumber()
                    .equals(accountNumber)) {

                return account;
            }
        }

        return null;
    }


    // Deposit

    public boolean deposit(
            String accountNumber,
            double amount) {

        Account account =
                getAccount(accountNumber);


        if (account == null) {
            return false;
        }


        Transaction transaction =
                new DepositTransaction(amount);


        return account.performTransaction(
                transaction);
    }


    // Withdraw

    public boolean withdraw(
            String accountNumber,
            double amount) {

        Account account =
                getAccount(accountNumber);


        if (account == null) {
            return false;
        }


        Transaction transaction =
                new WithdrawTransaction(amount);


        return account.performTransaction(
                transaction);
    }
}


// =========================
// MAIN BANKING SYSTEM
// =========================

public class BankingSystem {

    static Bank bank = new Bank();


    public static void main(String[] args)
            throws IOException {


        int port = Integer.parseInt(
                System.getenv().getOrDefault("PORT", "8080"));

        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(port),
                        0);


        // Home page

        server.createContext(
                "/",
                new HomeHandler());


        // Open account

        server.createContext(
                "/open",
                new OpenAccountHandler());


        // Deposit

        server.createContext(
                "/deposit",
                new DepositHandler());


        // Withdraw

        server.createContext(
                "/withdraw",
                new WithdrawHandler());


        // Transaction history

        server.createContext(
                "/history",
                new HistoryHandler());


        server.setExecutor(null);

        server.start();


        System.out.println(
                "Banking System started!");

        System.out.println(
                "Server running on port " + port);
    }


    // =========================
    // HOME PAGE
    // =========================

    static class HomeHandler
            implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange)
                throws IOException {


            String html =

                    "<html>" +
                    "<head>" +

                    "<title>Simple Banking System</title>" +

                    "<style>" +

                    "body {" +
                    "font-family: Arial;" +
                    "background-color: #f2f2f2;" +
                    "padding: 30px;" +
                    "}" +

                    ".container {" +
                    "max-width: 700px;" +
                    "margin: auto;" +
                    "background: white;" +
                    "padding: 30px;" +
                    "border-radius: 10px;" +
                    "}" +

                    ".box {" +
                    "border: 1px solid #ddd;" +
                    "padding: 20px;" +
                    "margin-top: 20px;" +
                    "border-radius: 8px;" +
                    "}" +

                    "input {" +
                    "width: 95%;" +
                    "padding: 10px;" +
                    "margin: 5px;" +
                    "}" +

                    "button {" +
                    "padding: 10px 20px;" +
                    "margin: 5px;" +
                    "cursor: pointer;" +
                    "}" +

                    "h1 {" +
                    "text-align: center;" +
                    "}" +

                    "</style>" +

                    "</head>" +

                    "<body>" +

                    "<div class='container'>" +

                    "<h1>Simple Banking System</h1>" +


                    // OPEN ACCOUNT

                    "<div class='box'>" +

                    "<h2>Open New Account</h2>" +

                    "<form action='/open' method='get'>" +

                    "<input type='text' " +
                    "name='name' " +
                    "placeholder='Customer Name' " +
                    "required>" +

                    "<input type='email' " +
                    "name='email' " +
                    "placeholder='Email' " +
                    "required>" +

                    "<input type='number' " +
                    "name='amount' " +
                    "placeholder='Initial Deposit' " +
                    "step='0.01' " +
                    "required>" +

                    "<button type='submit'>" +
                    "Open Account" +
                    "</button>" +

                    "</form>" +

                    "</div>" +


                    // DEPOSIT

                    "<div class='box'>" +

                    "<h2>Deposit Money</h2>" +

                    "<form action='/deposit' method='get'>" +

                    "<input type='text' " +
                    "name='account' " +
                    "placeholder='Account Number' " +
                    "required>" +

                    "<input type='number' " +
                    "name='amount' " +
                    "placeholder='Amount' " +
                    "step='0.01' " +
                    "required>" +

                    "<button type='submit'>" +
                    "Deposit" +
                    "</button>" +

                    "</form>" +

                    "</div>" +


                    // WITHDRAW

                    "<div class='box'>" +

                    "<h2>Withdraw Money</h2>" +

                    "<form action='/withdraw' method='get'>" +

                    "<input type='text' " +
                    "name='account' " +
                    "placeholder='Account Number' " +
                    "required>" +

                    "<input type='number' " +
                    "name='amount' " +
                    "placeholder='Amount' " +
                    "step='0.01' " +
                    "required>" +

                    "<button type='submit'>" +
                    "Withdraw" +
                    "</button>" +

                    "</form>" +

                    "</div>" +


                    // HISTORY

                    "<div class='box'>" +

                    "<h2>Transaction History</h2>" +

                    "<form action='/history' method='get'>" +

                    "<input type='text' " +
                    "name='account' " +
                    "placeholder='Account Number' " +
                    "required>" +

                    "<button type='submit'>" +
                    "View History" +
                    "</button>" +

                    "</form>" +

                    "</div>" +


                    "</div>" +

                    "</body>" +

                    "</html>";


            sendResponse(
                    exchange,
                    html);
        }
    }


    // =========================
    // OPEN ACCOUNT
    // =========================

    static class OpenAccountHandler
            implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange)
                throws IOException {


            String query =
                    exchange.getRequestURI()
                            .getQuery();


            String name =
                    getParameter(
                            query,
                            "name");


            String email =
                    getParameter(
                            query,
                            "email");


            String amountText =
                    getParameter(
                            query,
                            "amount");


            double amount;


            try {

                amount =
                        Double.parseDouble(
                                amountText);

            } catch (Exception e) {

                sendResponse(
                        exchange,
                        "<h2>Invalid amount.</h2>" +
                        "<a href='/'>Back</a>");

                return;
            }


            if (amount < 0) {

                sendResponse(
                        exchange,
                        "<h2>Initial deposit cannot be negative.</h2>" +
                        "<a href='/'>Back</a>");

                return;
            }


            Account account =
                    bank.openAccount(
                            name,
                            email,
                            amount);


            String response =

                    "<html><body>" +

                    "<h2>Account Created Successfully!</h2>" +

                    "<p>Customer Name: " +
                    name +
                    "</p>" +

                    "<p>Email: " +
                    email +
                    "</p>" +

                    "<p>Account Number: <b>" +
                    account.getAccountNumber() +
                    "</b></p>" +

                    "<p>Balance: Rs. " +
                    String.format("%.2f",
                            account.getBalance()) +
                    "</p>" +

                    "<a href='/'>Back to Home</a>" +

                    "</body></html>";


            sendResponse(
                    exchange,
                    response);
        }
    }


    // =========================
    // DEPOSIT
    // =========================

    static class DepositHandler
            implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange)
                throws IOException {


            String query =
                    exchange.getRequestURI()
                            .getQuery();


            String accountNumber =
                    getParameter(
                            query,
                            "account");


            String amountText =
                    getParameter(
                            query,
                            "amount");


            double amount;


            try {

                amount =
                        Double.parseDouble(
                                amountText);

            } catch (Exception e) {

                sendResponse(
                        exchange,
                        "<h2>Invalid amount.</h2>" +
                        "<a href='/'>Back</a>");

                return;
            }


            boolean success =
                    bank.deposit(
                            accountNumber,
                            amount);


            Account account =
                    bank.getAccount(
                            accountNumber);


            if (success) {

                sendResponse(
                        exchange,

                        "<html><body>" +

                        "<h2>Deposit Successful!</h2>" +

                        "<p>Amount Deposited: Rs. " +
                        String.format("%.2f", amount) +
                        "</p>" +

                        "<p>New Balance: Rs. " +
                        String.format("%.2f",
                                account.getBalance()) +
                        "</p>" +

                        "<a href='/'>Back to Home</a>" +

                        "</body></html>");

            } else {

                sendResponse(
                        exchange,

                        "<html><body>" +

                        "<h2>Deposit Failed</h2>" +

                        "<p>Check the account number and amount.</p>" +

                        "<a href='/'>Back to Home</a>" +

                        "</body></html>");
            }
        }
    }


    // =========================
    // WITHDRAW
    // =========================

    static class WithdrawHandler
            implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange)
                throws IOException {


            String query =
                    exchange.getRequestURI()
                            .getQuery();


            String accountNumber =
                    getParameter(
                            query,
                            "account");


            String amountText =
                    getParameter(
                            query,
                            "amount");


            double amount;


            try {

                amount =
                        Double.parseDouble(
                                amountText);

            } catch (Exception e) {

                sendResponse(
                        exchange,
                        "<h2>Invalid amount.</h2>" +
                        "<a href='/'>Back</a>");

                return;
            }


            Account account =
                    bank.getAccount(
                            accountNumber);


            if (account == null) {

                sendResponse(
                        exchange,

                        "<h2>Account not found.</h2>" +
                        "<a href='/'>Back</a>");

                return;
            }


            if (amount <= 0) {

                sendResponse(
                        exchange,

                        "<h2>Amount must be greater than zero.</h2>" +
                        "<a href='/'>Back</a>");

                return;
            }


            if (amount > account.getBalance()) {

                sendResponse(
                        exchange,

                        "<h2>Withdrawal Failed</h2>" +

                        "<p>Insufficient balance.</p>" +

                        "<p>Current Balance: Rs. " +
                        String.format("%.2f",
                                account.getBalance()) +
                        "</p>" +

                        "<a href='/'>Back to Home</a>");

                return;
            }


            boolean success =
                    bank.withdraw(
                            accountNumber,
                            amount);


            if (success) {

                sendResponse(
                        exchange,

                        "<html><body>" +

                        "<h2>Withdrawal Successful!</h2>" +

                        "<p>Amount Withdrawn: Rs. " +
                        String.format("%.2f", amount) +
                        "</p>" +

                        "<p>New Balance: Rs. " +
                        String.format("%.2f",
                                account.getBalance()) +
                        "</p>" +

                        "<a href='/'>Back to Home</a>" +

                        "</body></html>");

            } else {

                sendResponse(
                        exchange,

                        "<html><body>" +

                        "<h2>Withdrawal Failed</h2>" +

                        "<p>Transaction could not be completed.</p>" +

                        "<a href='/'>Back to Home</a>" +

                        "</body></html>");
            }
        }
    }


    // =========================
    // TRANSACTION HISTORY
    // =========================

    static class HistoryHandler
            implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange)
                throws IOException {


            String query =
                    exchange.getRequestURI()
                            .getQuery();


            String accountNumber =
                    getParameter(
                            query,
                            "account");


            Account account =
                    bank.getAccount(
                            accountNumber);


            if (account == null) {

                sendResponse(
                        exchange,

                        "<html><body>" +

                        "<h2>Account not found.</h2>" +

                        "<a href='/'>Back to Home</a>" +

                        "</body></html>");

                return;
            }


            StringBuilder response =
                    new StringBuilder();


            response.append(
                    "<html><body>");

            response.append(
                    "<h2>Transaction History</h2>");


            response.append(
                    "<p>Account Number: <b>")
                    .append(account.getAccountNumber())
                    .append("</b></p>");


            response.append(
                    "<p>Customer Name: <b>")
                    .append(account.getAccountHolder()
                            .getName())
                    .append("</b></p>");


            response.append(
                    "<p>Current Balance: <b>Rs. ")
                    .append(String.format("%.2f",
                            account.getBalance()))
                    .append("</b></p>");


            List<Transaction> transactions =
                    account.getTransactions();


            if (transactions.isEmpty()) {

                response.append(
                        "<p>No transactions found.</p>");

            } else {

                response.append(
                        "<ol>");

                for (Transaction transaction :
                        transactions) {

                    response.append(
                            "<li>")
                            .append(transaction.getDetails())
                            .append("</li>");
                }

                response.append(
                        "</ol>");
            }


            response.append(
                    "<br><a href='/'>Back to Home</a>");

            response.append(
                    "</body></html>");


            sendResponse(
                    exchange,
                    response.toString());
        }
    }


    // =========================
    // GET PARAMETER
    // =========================

    static String getParameter(
            String query,
            String parameter) {

        if (query == null) {
            return "";
        }


        String[] pairs =
                query.split("&");


        for (String pair : pairs) {

            String[] keyValue =
                    pair.split("=", 2);


            if (keyValue.length == 2 &&
                    keyValue[0].equals(parameter)) {

                try {

                    return URLDecoder.decode(
                            keyValue[1],
                            StandardCharsets.UTF_8);

                } catch (Exception e) {

                    return keyValue[1];
                }
            }
        }


        return "";
    }


    // =========================
    // SEND HTTP RESPONSE
    // =========================

    static void sendResponse(
            HttpExchange exchange,
            String response)
            throws IOException {


        byte[] bytes =
                response.getBytes(
                        StandardCharsets.UTF_8);


        exchange.getResponseHeaders()
                .set("Content-Type",
                        "text/html; charset=UTF-8");


        exchange.sendResponseHeaders(
                200,
                bytes.length);


        OutputStream output =
                exchange.getResponseBody();


        output.write(bytes);

        output.close();
    }
}