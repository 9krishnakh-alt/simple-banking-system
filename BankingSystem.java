import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;

public class BankingSystem {
    public static void main(String[] args) throws Exception {
        Database.initialize();
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", BankingSystem::home);
        server.createContext("/open", BankingSystem::open);
        server.createContext("/deposit", BankingSystem::deposit);
        server.createContext("/withdraw", BankingSystem::withdraw);
        server.createContext("/history", BankingSystem::history);
        server.createContext("/database", BankingSystem::database);
        server.createContext("/account", BankingSystem::account);
        server.start();
        System.out.println("Simple Banking System running on port " + port);
    }

    private static String start(String title) {
        return "<!doctype html><html><head><meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<title>"+esc(title)+"</title><style>" +
            "*{box-sizing:border-box}body{margin:0;font-family:Arial;background:#f3f6fb;color:#172033}" +
            ".hero{background:linear-gradient(135deg,#172554,#2563eb);color:white;padding:28px 16px}" +
            ".hero h1{margin:0 0 7px}.wrap{max-width:1050px;margin:auto;padding:0 15px}" +
            "nav{background:white;padding:12px;box-shadow:0 2px 12px #0001;position:sticky;top:0}" +
            "nav a{display:inline-block;background:#eff6ff;color:#1d4ed8;padding:9px 12px;margin:3px;border-radius:8px;text-decoration:none;font-weight:bold}" +
            ".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:17px;margin-top:20px}" +
            ".card,.stat{background:white;padding:20px;border-radius:14px;box-shadow:0 5px 18px #0001;margin:18px 0}" +
            ".stat{background:#eaf2ff}.num{font-size:27px;font-weight:bold;color:#1d4ed8}" +
            "input{width:100%;padding:12px;margin:6px 0;border:1px solid #cbd5e1;border-radius:8px}" +
            "button{background:#2563eb;color:white;border:0;border-radius:8px;padding:11px 15px;font-weight:bold;margin-top:5px}" +
            "table{width:100%;border-collapse:collapse;min-width:600px}th,td{padding:10px;border-bottom:1px solid #e2e8f0;text-align:left}th{background:#eff6ff}" +
            ".table{overflow:auto}.ok{background:#dcfce7;color:#166534;padding:14px;border-radius:9px;margin-top:20px}" +
            ".bad{background:#fee2e2;color:#991b1b;padding:14px;border-radius:9px;margin-top:20px}" +
            "</style></head><body><div class='hero'><div class='wrap'><h1>🏦 Simple Banking System</h1>" +
            "<div>Java OOP • JDBC • SQLite Database</div></div></div><nav><div class='wrap'>" +
            "<a href='/'>Dashboard</a><a href='/account'>Account</a><a href='/history'>History</a><a href='/database'>🗄️ Database</a>" +
            "</div></nav><main class='wrap'>";
    }

    private static String end() {
        return "</main><div style='text-align:center;padding:30px;color:#64748b'>Simple Banking System</div></body></html>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace(""","&quot;").replace("'","&#39;");
    }

    private static String param(String q, String k) {
        if (q == null) return "";
        for (String x : q.split("&")) {
            String[] a = x.split("=",2);
            if (a.length == 2 && a[0].equals(k)) {
                try { return URLDecoder.decode(a[1], StandardCharsets.UTF_8); }
                catch (Exception e) { return a[1]; }
            }
        }
        return "";
    }

    private static void send(HttpExchange e, String html) throws IOException {
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type","text/html;charset=UTF-8");
        e.sendResponseHeaders(200,b.length);
        try (OutputStream o=e.getResponseBody()) { o.write(b); }
    }

    private static void error(HttpExchange e, String m) throws IOException {
        send(e,start("Error")+"<div class='bad'><h2>Error</h2>"+esc(m)+"</div>"+end());
    }

    private static String form(String title,String action,String fields,String button) {
        return "<div class='card'><h2>"+title+"</h2><form action='"+action+"'>"+
               fields+"<button>"+button+"</button></form></div>";
    }

    private static void home(HttpExchange e) throws IOException {
        try {
            String h=start("Dashboard");
            h+="<div class='grid'><div class='stat'>Customers<div class='num'>"+Database.count("customers")+
                "</div></div><div class='stat'>Accounts<div class='num'>"+Database.count("accounts")+
                "</div></div><div class='stat'>Transactions<div class='num'>"+Database.count("transactions")+
                "</div></div><div class='stat'>Total Balance<div class='num'>₹"+
                String.format("%.2f",Database.totalBalance())+"</div></div></div><div class='grid'>";
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
            h+="</div><div class='card'><h2>🗄️ Database</h2><p>View the actual SQLite tables stored through JDBC.</p>"+
               "<a href='/database'><button>View Database Tables</button></a></div>";
            send(e,h+end());
        } catch(Exception x) { error(e,x.getMessage()); }
    }

    private static void open(HttpExchange e) throws IOException {
        try {
            String q=e.getRequestURI().getRawQuery();
            String n=param(q,"name"), mail=param(q,"email");
            double initial=Double.parseDouble(param(q,"initial"));
            int no=new Bank().openAccount(n,mail,initial);
            if(no<0){error(e,"Invalid account information.");return;}
            send(e,start("Account Created")+"<div class='ok'><h2>Account created!</h2><h1>"+no+
                "</h1><p>Customer: <b>"+esc(n)+"</b></p><p>Initial balance: <b>₹"+
                String.format("%.2f",initial)+"</b></p></div>"+end());
        } catch(Exception x){error(e,x.getMessage());}
    }

    private static void deposit(HttpExchange e) throws IOException {
        try {
            String q=e.getRequestURI().getRawQuery();
            int no=Integer.parseInt(param(q,"account"));
            double a=Double.parseDouble(param(q,"amount"));
            Bank bank=new Bank();
            boolean ok=bank.deposit(no,a);
            Account x=bank.getAccount(no);
            if(!ok){error(e,"Deposit failed. Check account number and amount.");return;}
            send(e,start("Deposit")+"<div class='ok'><h2>Deposit successful!</h2><p>Account: "+no+
                "</p><p>Deposited: ₹"+String.format("%.2f",a)+"</p><p>New balance: ₹"+
                String.format("%.2f",x.getBalance())+"</p></div>"+end());
        } catch(Exception x){error(e,"Invalid deposit request.");}
    }

    private static void withdraw(HttpExchange e) throws IOException {
        try {
            String q=e.getRequestURI().getRawQuery();
            int no=Integer.parseInt(param(q,"account"));
            double a=Double.parseDouble(param(q,"amount"));
            Bank bank=new Bank();
            Account before=bank.getAccount(no);
            if(before==null){error(e,"Account not found.");return;}
            boolean ok=bank.withdraw(no,a);
            Account after=bank.getAccount(no);
            if(ok) send(e,start("Withdrawal")+"<div class='ok'><h2>Withdrawal successful!</h2>"+
                "<p>Account: "+no+"</p><p>Withdrawn: ₹"+String.format("%.2f",a)+
                "</p><p>New balance: ₹"+String.format("%.2f",after.getBalance())+
                "</p></div>"+end());
            else error(e,"Withdrawal failed. Insufficient balance or invalid amount. Balance: ₹"+
                String.format("%.2f",before.getBalance()));
        } catch(Exception x){error(e,"Invalid withdrawal request.");}
    }

    private static void account(HttpExchange e) throws IOException {
        try {
            String q=e.getRequestURI().getRawQuery();
            if(q==null||param(q,"account").isBlank()){
                send(e,start("Account")+"<div class='card'><h2>Find Account</h2><form>"+
                    "<input name='account' type='number' placeholder='Account Number' required>"+
                    "<button>View Account</button></form></div>"+end());return;
            }
            Account a=new Bank().getAccount(Integer.parseInt(param(q,"account")));
            String h=start("Account Details");
            if(a==null) h+="<div class='bad'>Account not found.</div>";
            else h+="<div class='card'><h2>🏦 Account #"+a.getAccountNumber()+
                "</h2><p><b>Customer:</b> "+esc(a.getAccountHolder().getName())+
                "</p><p><b>Email:</b> "+esc(a.getAccountHolder().getEmail())+
                "</p><h2>Balance: ₹"+String.format("%.2f",a.getBalance())+"</h2></div>";
            send(e,h+end());
        } catch(Exception x){error(e,"Invalid account number.");}
    }

    private static void history(HttpExchange e) throws IOException {
        try {
            String q=e.getRequestURI().getRawQuery();
            if(q==null||param(q,"account").isBlank()){
                send(e,start("History")+"<div class='card'><h2>Transaction History</h2><form>"+
                    "<input name='account' type='number' placeholder='Account Number' required>"+
                    "<button>View History</button></form></div>"+end());return;
            }
            int no=Integer.parseInt(param(q,"account"));
            Account a=new Bank().getAccount(no);
            if(a==null){error(e,"Account not found.");return;}
            String h=start("History")+"<div class='card'><h2>📜 Account #"+no+
                "</h2><p>Customer: <b>"+esc(a.getAccountHolder().getName())+
                "</b> | Balance: <b>₹"+String.format("%.2f",a.getBalance())+
                "</b></p><div class='table'><table><tr><th>ID</th><th>Type</th><th>Amount</th><th>Date</th></tr>";
            try(Connection c=Database.getConnection();
                PreparedStatement p=c.prepareStatement(
                    "SELECT id,type,amount,date FROM transactions WHERE account_number=? ORDER BY id DESC")){
                p.setInt(1,no);
                try(ResultSet r=p.executeQuery()){
                    while(r.next()) h+="<tr><td>"+r.getInt(1)+"</td><td>"+
                        esc(r.getString(2))+"</td><td>₹"+String.format("%.2f",r.getDouble(3))+
                        "</td><td>"+esc(r.getString(4))+"</td></tr>";
                }
            }
            h+="</table></div></div>";
            send(e,h+end());
        } catch(Exception x){error(e,"Could not load history.");}
    }

    private static void database(HttpExchange e) throws IOException {
        try {
            String h=start("Database Viewer")+"<div class='card'><h2>🗄️ SQLite Database</h2>"+
                "<p>These are the actual records stored by JDBC.</p></div>";
            h+=table("Customers Table","SELECT id,name,email FROM customers",
                "ID","Name","Email");
            h+=table("Accounts Table",
                "SELECT account_number,customer_id,balance,created_at FROM accounts",
                "Account Number","Customer ID","Balance","Created At");
            h+=table("Transactions Table",
                "SELECT id,account_number,type,amount,date FROM transactions",
                "ID","Account Number","Type","Amount","Date");
            send(e,h+end());
        } catch(Exception x){error(e,x.getMessage());}
    }

    private static String table(String title,String sql,String... headers)
            throws SQLException {
        StringBuilder h=new StringBuilder("<div class='card'><h2>"+title+
            "</h2><div class='table'><table><tr>");
        for(String x:headers) h.append("<th>").append(esc(x)).append("</th>");
        h.append("</tr>");
        try(Connection c=Database.getConnection();Statement s=c.createStatement();
            ResultSet r=s.executeQuery(sql)){
            ResultSetMetaData m=r.getMetaData();
            while(r.next()){
                h.append("<tr>");
                for(int i=1;i<=m.getColumnCount();i++)
                    h.append("<td>").append(esc(String.valueOf(r.getObject(i)))).append("</td>");
                h.append("</tr>");
            }
        }
        return h.append("</table></div></div>").toString();
    }
}
