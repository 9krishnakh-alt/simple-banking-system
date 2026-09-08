
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BankingSystem {
    static final String DB = "jdbc:sqlite:" +
            System.getenv().getOrDefault("DB_PATH", "data/banking.db");
    static final DateTimeFormatter F =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static Connection db() throws SQLException {
        return DriverManager.getConnection(DB);
    }

    static void initDB() throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection c=db(); Statement s=c.createStatement()) {
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

    // Abstraction
    static abstract class Transaction {
        protected final double amount;
        protected final String date = LocalDateTime.now().format(F);
        Transaction(double amount){this.amount=amount;}
        abstract String type();
        abstract boolean process(Account a) throws SQLException;
    }

    // Inheritance
    static class DepositTransaction extends Transaction {
        DepositTransaction(double a){super(a);}
        String type(){return "DEPOSIT";}
        boolean process(Account a) throws SQLException {
            if(amount<=0)return false;
            a.change(amount); a.log(type(),amount,date); return true;
        }
    }
    static class WithdrawTransaction extends Transaction {
        WithdrawTransaction(double a){super(a);}
        String type(){return "WITHDRAW";}
        boolean process(Account a) throws SQLException {
            if(amount<=0 || amount>a.balance)return false;
            a.change(-amount); a.log(type(),amount,date); return true;
        }
    }

    // Encapsulation
    static class Customer {
        private final int id; private final String name,email;
        Customer(int i,String n,String e){id=i;name=n;email=e;}
        int id(){return id;} String name(){return name;} String email(){return email;}
    }

    static class Account {
        private final int number; private final Customer customer;
        private double balance;
        Account(int n,Customer c,double b){number=n;customer=c;balance=b;}
        int number(){return number;} Customer customer(){return customer;}
        double balance(){return balance;}

        void change(double amount)throws SQLException{
            balance+=amount;
            try(Connection c=db();PreparedStatement p=c.prepareStatement(
                    "UPDATE accounts SET balance=? WHERE account_number=?")){
                p.setDouble(1,balance);p.setInt(2,number);p.executeUpdate();
            }
        }
        void log(String type,double amount,String date)throws SQLException{
            try(Connection c=db();PreparedStatement p=c.prepareStatement(
                    "INSERT INTO transactions(account_number,type,amount,date) VALUES(?,?,?,?)")){
                p.setInt(1,number);p.setString(2,type);p.setDouble(3,amount);
                p.setString(4,date);p.executeUpdate();
            }
        }
        boolean perform(Transaction t)throws SQLException{return t.process(this);}
    }

    static class Bank {
        int next()throws SQLException{
            try(Connection c=db();Statement s=c.createStatement();
                ResultSet r=s.executeQuery("SELECT COALESCE(MAX(account_number),1000)+1 FROM accounts")){
                return r.next()?r.getInt(1):1001;
            }
        }
        int open(String name,String email,double initial)throws SQLException{
            if(name.isBlank()||email.isBlank()||initial<0)return -1;
            int no=next(); String now=LocalDateTime.now().format(F);
            try(Connection c=db()){
                c.setAutoCommit(false);
                try{
                    int id;
                    try(PreparedStatement p=c.prepareStatement(
                            "INSERT INTO customers(name,email) VALUES(?,?)",
                            Statement.RETURN_GENERATED_KEYS)){
                        p.setString(1,name.trim());p.setString(2,email.trim());p.executeUpdate();
                        try(ResultSet r=p.getGeneratedKeys()){r.next();id=r.getInt(1);}
                    }
                    try(PreparedStatement p=c.prepareStatement(
                            "INSERT INTO accounts(account_number,customer_id,balance,created_at) VALUES(?,?,?,?)")){
                        p.setInt(1,no);p.setInt(2,id);p.setDouble(3,initial);p.setString(4,now);p.executeUpdate();
                    }
                    if(initial>0)try(PreparedStatement p=c.prepareStatement(
                            "INSERT INTO transactions(account_number,type,amount,date) VALUES(?,'DEPOSIT',?,?)")){
                        p.setInt(1,no);p.setDouble(2,initial);p.setString(3,now);p.executeUpdate();
                    }
                    c.commit();return no;
                }catch(Exception e){c.rollback();throw e;}
            }
        }
        Account get(int no)throws SQLException{
            String q="SELECT a.account_number,a.balance,c.id,c.name,c.email " +
                    "FROM accounts a JOIN customers c ON a.customer_id=c.id WHERE a.account_number=?";
            try(Connection c=db();PreparedStatement p=c.prepareStatement(q)){
                p.setInt(1,no);try(ResultSet r=p.executeQuery()){
                    if(!r.next())return null;
                    return new Account(r.getInt(1),
                            new Customer(r.getInt(3),r.getString(4),r.getString(5)),
                            r.getDouble(2));
                }
            }
        }
        boolean deposit(int no,double amount)throws SQLException{
            Account a=get(no);return a!=null&&a.perform(new DepositTransaction(amount));
        }
        boolean withdraw(int no,double amount)throws SQLException{
            Account a=get(no);return a!=null&&a.perform(new WithdrawTransaction(amount));
        }
    }

    public static void main(String[] args)throws Exception{
        initDB();
        int port=Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));
        HttpServer s=HttpServer.create(new InetSocketAddress(port),0);
        s.createContext("/",BankingSystem::home);
        s.createContext("/open",BankingSystem::open);
        s.createContext("/deposit",BankingSystem::deposit);
        s.createContext("/withdraw",BankingSystem::withdraw);
        s.createContext("/history",BankingSystem::history);
        s.createContext("/database",BankingSystem::database);
        s.createContext("/account",BankingSystem::account);
        s.start();
        System.out.println("Banking System running on port "+port);
    }

    static String start(String title){
        return "<!doctype html><html><head><meta charset='UTF-8'>" +
        "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
        "<title>"+esc(title)+"</title><style>"+
        "*{box-sizing:border-box}body{margin:0;font-family:Arial;background:#f3f6fb;color:#172033}"+
        ".hero{background:linear-gradient(135deg,#172554,#2563eb);color:white;padding:28px 16px}"+
        ".hero h1{margin:0 0 7px}.wrap{max-width:1050px;margin:auto;padding:0 15px}"+
        "nav{background:white;padding:12px;box-shadow:0 2px 12px #0001;position:sticky;top:0}"+
        "nav a{display:inline-block;background:#eff6ff;color:#1d4ed8;padding:9px 12px;margin:3px;border-radius:8px;text-decoration:none;font-weight:bold}"+
        ".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:17px;margin-top:20px}"+
        ".card,.stat{background:white;padding:20px;border-radius:14px;box-shadow:0 5px 18px #0001;margin:18px 0}"+
        ".stat{background:#eaf2ff}.num{font-size:27px;font-weight:bold;color:#1d4ed8}"+
        "input{width:100%;padding:12px;margin:6px 0;border:1px solid #cbd5e1;border-radius:8px}"+
        "button{background:#2563eb;color:white;border:0;border-radius:8px;padding:11px 15px;font-weight:bold;margin-top:5px}"+
        "table{width:100%;border-collapse:collapse;min-width:600px}th,td{padding:10px;border-bottom:1px solid #e2e8f0;text-align:left}th{background:#eff6ff}"+
        ".table{overflow:auto}.ok{background:#dcfce7;color:#166534;padding:14px;border-radius:9px;margin-top:20px}"+
        ".bad{background:#fee2e2;color:#991b1b;padding:14px;border-radius:9px;margin-top:20px}"+
        "</style></head><body><div class='hero'><div class='wrap'><h1>🏦 Simple Banking System</h1>"+
        "<div>Java OOP • JDBC • SQLite Database</div></div></div><nav><div class='wrap'>"+
        "<a href='/'>Dashboard</a><a href='/account'>Account</a><a href='/history'>History</a><a href='/database'>🗄️ Database</a>"+
        "</div></nav><main class='wrap'>";
    }
    static String end(){return "</main><div style='text-align:center;padding:30px;color:#64748b'>Simple Banking System</div></body></html>";}
    static String esc(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    static String param(String q,String k){
        if(q==null)return "";for(String x:q.split("&")){String[] a=x.split("=",2);
        if(a.length==2&&a[0].equals(k))try{return URLDecoder.decode(a[1],StandardCharsets.UTF_8);}catch(Exception e){return a[1];}}
        return "";
    }
    static void send(HttpExchange e,String h)throws IOException{
        byte[] b=h.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","text/html;charset=UTF-8");
        e.sendResponseHeaders(200,b.length);try(OutputStream o=e.getResponseBody()){o.write(b);}
    }
    static void error(HttpExchange e,String m)throws IOException{send(e,start("Error")+"<div class='bad'><h2>Error</h2>"+esc(m)+"</div>"+end());}
    static int count(String t)throws SQLException{
        try(Connection c=db();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+t)){return r.next()?r.getInt(1):0;}
    }
    static double total()throws SQLException{
        try(Connection c=db();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COALESCE(SUM(balance),0) FROM accounts")){return r.next()?r.getDouble(1):0;}
    }

    static void home(HttpExchange e)throws IOException{
        try{
            String h=start("Dashboard");
            h+="<div class='grid'><div class='stat'>Customers<div class='num'>"+count("customers")+
            "</div></div><div class='stat'>Accounts<div class='num'>"+count("accounts")+
            "</div></div><div class='stat'>Transactions<div class='num'>"+count("transactions")+
            "</div></div><div class='stat'>Total Balance<div class='num'>₹"+String.format("%.2f",total())+"</div></div></div>";
            h+="<div class='grid'>";
            h+=form("👤 Open New Account","/open",
                    "<input name='name' placeholder='Customer Name' required>"+
                    "<input name='email' type='email' placeholder='Email' required>"+
                    "<input name='initial' type='number' step='0.01' min='0' placeholder='Initial Deposit' required>",
                    "Open Account");
            h+=form("💰 Deposit Money","/deposit",
                    "<input name='account' type='number' placeholder='Account Number' required>"+
                    "<input name='amount' type='number' step='0.01' min='0.01' placeholder='Amount' required>",
                    "Deposit");
            h+=form("💸 Withdraw Money","/withdraw",
                    "<input name='account' type='number' placeholder='Account Number' required>"+
                    "<input name='amount' type='number' step='0.01' min='0.01' placeholder='Amount' required>",
                    "Withdraw");
            h+=form("📜 Transaction History","/history",
                    "<input name='account' type='number' placeholder='Account Number' required>","View History");
            h+="</div><div class='card'><h2>🗄️ Database</h2><p>Show the real SQLite tables stored through JDBC.</p><a href='/database'><button>View Database Tables</button></a></div>";
            send(e,h+end());
        }catch(Exception x){error(e,x.getMessage());}
    }
    static String form(String title,String action,String fields,String button){
        return "<div class='card'><h2>"+title+"</h2><form action='"+action+"'>"+fields+"<button>"+button+"</button></form></div>";
    }

    static void open(HttpExchange e)throws IOException{
        try{
            String q=e.getRequestURI().getRawQuery();String n=param(q,"name"),mail=param(q,"email");
            double initial=Double.parseDouble(param(q,"initial"));int no=new Bank().open(n,mail,initial);
            if(no<0){error(e,"Invalid account information.");return;}
            send(e,start("Account Created")+"<div class='ok'><h2>Account created!</h2><h1>"+no+
                    "</h1><p>Customer: <b>"+esc(n)+"</b></p><p>Initial balance: <b>₹"+
                    String.format("%.2f",initial)+"</b></p></div>"+end());
        }catch(Exception x){error(e,x.getMessage());}
    }
    static void deposit(HttpExchange e)throws IOException{
        try{
            String q=e.getRequestURI().getRawQuery();int no=Integer.parseInt(param(q,"account"));
            double a=Double.parseDouble(param(q,"amount"));boolean ok=new Bank().deposit(no,a);Account x=new Bank().get(no);
            if(!ok){error(e,"Deposit failed. Check account number and amount.");return;}
            send(e,start("Deposit")+"<div class='ok'><h2>Deposit successful!</h2><p>Account: "+no+
                    "</p><p>Deposited: ₹"+String.format("%.2f",a)+"</p><p>New balance: ₹"+
                    String.format("%.2f",x.balance())+"</p></div>"+end());
        }catch(Exception x){error(e,"Invalid deposit request.");}
    }
    static void withdraw(HttpExchange e)throws IOException{
        try{
            String q=e.getRequestURI().getRawQuery();int no=Integer.parseInt(param(q,"account"));
            double a=Double.parseDouble(param(q,"amount"));Account before=new Bank().get(no);
            if(before==null){error(e,"Account not found.");return;}
            boolean ok=new Bank().withdraw(no,a);Account after=new Bank().get(no);
            String msg=ok?"<div class='ok'><h2>Withdrawal successful!</h2><p>New balance: ₹"+
                    String.format("%.2f",after.balance())+"</p></div>":
                    "<div class='bad'><h2>Withdrawal failed</h2><p>Insufficient balance or invalid amount.</p><p>Balance: ₹"+
                    String.format("%.2f",before.balance())+"</p></div>";
            send(e,start("Withdrawal")+msg+end());
        }catch(Exception x){error(e,"Invalid withdrawal request.");}
    }

    static void account(HttpExchange e)throws IOException{
        try{
            String q=e.getRequestURI().getRawQuery();
            if(q==null||param(q,"account").isBlank()){
                send(e,start("Account")+"<div class='card'><h2>Find Account</h2><form><input name='account' type='number' placeholder='Account Number' required><button>View Account</button></form></div>"+end());return;
            }
            Account a=new Bank().get(Integer.parseInt(param(q,"account")));
            String h=start("Account Details");
            if(a==null)h+="<div class='bad'>Account not found.</div>";
            else h+="<div class='card'><h2>🏦 Account #"+a.number()+"</h2><p><b>Customer:</b> "+esc(a.customer().name())+
                    "</p><p><b>Email:</b> "+esc(a.customer().email())+"</p><h2>Balance: ₹"+
                    String.format("%.2f",a.balance())+"</h2></div>";
            send(e,h+end());
        }catch(Exception x){error(e,"Invalid account number.");}
    }

    static void history(HttpExchange e)throws IOException{
        try{
            String q=e.getRequestURI().getRawQuery();
            if(q==null||param(q,"account").isBlank()){
                send(e,start("History")+"<div class='card'><h2>Transaction History</h2><form><input name='account' type='number' placeholder='Account Number' required><button>View History</button></form></div>"+end());return;
            }
            int no=Integer.parseInt(param(q,"account"));Account a=new Bank().get(no);if(a==null){error(e,"Account not found.");return;}
            String h=start("History")+"<div class='card'><h2>📜 Account #"+no+"</h2><p>Customer: <b>"+esc(a.customer().name())+
                    "</b> | Balance: <b>₹"+String.format("%.2f",a.balance())+"</b></p><div class='table'><table><tr><th>ID</th><th>Type</th><th>Amount</th><th>Date</th></tr>";
            try(Connection c=db();PreparedStatement p=c.prepareStatement("SELECT id,type,amount,date FROM transactions WHERE account_number=? ORDER BY id DESC")){
                p.setInt(1,no);try(ResultSet r=p.executeQuery()){while(r.next())h+="<tr><td>"+r.getInt(1)+"</td><td>"+esc(r.getString(2))+"</td><td>₹"+
                        String.format("%.2f",r.getDouble(3))+"</td><td>"+esc(r.getString(4))+"</td></tr>";}}
            h+="</table></div></div>";send(e,h+end());
        }catch(Exception x){error(e,"Could not load history.");}
    }

    static void database(HttpExchange e)throws IOException{
        try{
            String h=start("Database Viewer")+"<div class='card'><h2>🗄️ SQLite Database</h2><p>These are the actual records stored by JDBC.</p></div>";
            h+=table("Customers","SELECT id,name,email FROM customers","ID","Name","Email");
            h+=table("Accounts","SELECT account_number,customer_id,balance,created_at FROM accounts","Account Number","Customer ID","Balance","Created At");
            h+=table("Transactions","SELECT id,account_number,type,amount,date FROM transactions","ID","Account Number","Type","Amount","Date");
            send(e,h+end());
        }catch(Exception x){error(e,x.getMessage());}
    }

    static String table(String title,String sql,String... headers)throws SQLException{
        StringBuilder h=new StringBuilder("<div class='card'><h2>"+title+" Table</h2><div class='table'><table><tr>");
        for(String x:headers)h.append("<th>").append(x).append("</th>");h.append("</tr>");
        try(Connection c=db();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){
            ResultSetMetaData m=r.getMetaData();while(r.next()){h.append("<tr>");
                for(int i=1;i<=m.getColumnCount();i++){Object v=r.getObject(i);h.append("<td>").append(esc(String.valueOf(v))).append("</td>");}
                h.append("</tr>");}}
        return h.append("</table></div></div>").toString();
    }
}
