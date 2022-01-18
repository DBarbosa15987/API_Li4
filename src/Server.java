import java.net.ServerSocket;
import java.net.Socket;

public class Server {


    public static void main(String[] args) throws Exception {

        ServerSocket ss = new ServerSocket(8888);

        while(true){

            Socket s = ss.accept();
            System.out.println("Conexão aceite da porta: " + s.getPort());
            Thread t = new Thread(new ServerWorker(s));
            t.start();

        }




    }
}
