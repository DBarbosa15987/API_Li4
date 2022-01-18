import netscape.javascript.JSObject;

import java.io.*;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;


public class ServerWorker implements Runnable{

    
    private Socket s;
    private Connection c;
    
    public ServerWorker(Socket s) throws InstantiationException, IllegalAccessException, ClassNotFoundException{
        this.s=s;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.c = DriverManager.getConnection("jdbc:mysql://localhost/mydb?", "root", "bomdia123");
        } catch (SQLException ex) {
            // handle any errors
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
    }


    @Override
    public void run() {
        
        try {
            
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            String a = in.readUTF();
            System.out.println(a);

            switch (a) {


                case "getLojas":
                    System.out.println("bomdia amiguinhos");
                    java.sql.Statement statement = c.createStatement();
                    ResultSet rs = statement.executeQuery("SELECT * FROM utilizador");
                    while(rs.next()){
                        User user = new User("nome");
                        ObjectOutputStream o = new ObjectOutputStream(out);
                        o.writeObject(user);
                        o.flush();
                        out.flush();
                        
                    }

                    break;

                


            
                default:
                    break;
            }


            out.close();
            in.close();
            s.close();

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println("fbhuaigfyduf");

        
    }

    



    
}
