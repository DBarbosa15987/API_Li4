import netscape.javascript.JSObject;

import java.io.*;
import java.net.Socket;
import java.sql.*;


public class ServerWorker implements Runnable{

    
    private Socket s;
    private Connection c;
    
    public ServerWorker(Socket s) throws InstantiationException, IllegalAccessException, ClassNotFoundException{
        this.s=s;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.c = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "root", "bomdia123");
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
            String query = in.readUTF();
            System.out.println(query);
            ResultSet rs;
            java.sql.Statement statement = c.createStatement();

            /*
            Queries:

            -> getUser (tudo)
            -> autenticaUser OK
            -> criarConta OK
            -> checkUserName OK
            -> alterarPassword OK
            -> getLoja (tudo)
            -> getLojaPreview
            -> getCategorias

             */

            switch (query) {
                //isto vai acontecer apenas quando o user visita o seu perfil
                case "getUser" -> {

                    //Como esta query só vai ser usada quando o user já estiver autenticado, o username já estará em cache
                    String username = in.readUTF();
                    System.out.println(username);

                    //Obter a informação do user
                    rs = statement.executeQuery("SELECT `email`,`nomeCompleto`,`morada` FROM utilizador WHERE `username`='" + username + "';");

                    //Enviar a informação do user
                    out.writeUTF(rs.getString("email"));
                    out.writeUTF(rs.getString("nomeCompleto"));
                    out.writeUTF(rs.getString("morada"));

                    //Obter a informação dos favoritos
                    rs = statement.executeQuery("SELECT * FROM favorito WHERE `username`='" + username + "';");

                    //Enviar a informação dos favortios
                    out.writeInt(rs.getFetchSize());
                    while(rs.next()) {
                        out.writeUTF(rs.getString("idLoja"));
                    }

                    //Obter a informação dos votos
                    rs = statement.executeQuery("SELECT * FROM voto WHERE `utilizador_username`='" + username + "';");

                    //Enviar a informação dos votos
                    out.writeInt(rs.getFetchSize());
                    while(rs.next()){
                        out.writeUTF(rs.getString("categoria_nomeCategoria"));
                        out.writeUTF(rs.getString("loja_idloja"));
                        out.writeBoolean(rs.getBoolean("voto"));
                    }

                    //Obter a informação dos comentários
                    rs = statement.executeQuery("SELECT * FROM comentario WHERE `username`='" + username + "';");

                    //Enviar informação os comentários
                    out.writeInt(rs.getFetchSize());
                    while(rs.next()){
                        out.writeUTF(rs.getString("idLoja"));
                        out.writeUTF(rs.getString("comentarioText"));
                        var timestamp = rs.getTimestamp("data");
                        out.writeLong(timestamp.getTime());

                    }

                    out.flush();
                }

                //quando um user é auteticado apenas é enviado uma confirmação e o seu username
                case "autenticaUser" -> {

                    //Receber username e pass que o user introduz
                    String usernameInput = in.readUTF();
                    String passwordInput = in.readUTF();


                    rs = statement.executeQuery("SELECT `username` FROM utilizador WHERE `username`='" + usernameInput +"' AND `password`='" + passwordInput + "';");

                    boolean autenticado = rs.getFetchSize()!=0;
                    out.writeBoolean(autenticado);
                    out.flush();

                    //User autenticado, enviar agora o username
                    if(autenticado){
                        out.writeUTF(rs.getString("username"));
                        out.flush();
                    }

                }


                case "criarConta" -> {

                    String usernameInput = in.readUTF();
                    String passwordInput = in.readUTF();
                    String nomeCompletoInput = in.readUTF();
                    String moradaInput = in.readUTF();
                    String emailInput = in.readUTF();



                    //Verificar se o userName está disponível
                    rs = statement.executeQuery("SELECT * FROM utilizador WHERE `username`='" + usernameInput + "';");
                    boolean userNameDisponivel = rs.getFetchSize()==0;
                    out.writeBoolean(userNameDisponivel);

                    //Verificar se o email está disponível
                    rs = statement.executeQuery("SELECT * FROM utilizador WHERE `email`='" + emailInput + "';");
                    boolean emailDisponivel = rs.getFetchSize()==0;
                    out.writeBoolean(emailDisponivel);
                    out.flush();

                    //Credenciais confirmadas, inserir User na db
                    if(userNameDisponivel&&emailDisponivel) {
                        rs = statement.executeQuery("INSERT INTO utilizador VALUES (" + usernameInput +"," + emailInput + "," + passwordInput + "," + nomeCompletoInput + "," + moradaInput + ")");
                    }

                }

                case "checkUserName" -> {

                    //Receber username que o user introduz
                    String usernameInput = in.readUTF();

                    rs = statement.executeQuery("SELECT * FROM utilizador WHERE `username`='" + usernameInput + "';");

                    //username é único, por isso se não existir na base de dados, está disponível
                    boolean userNameDisponivel = rs.getFetchSize()==0;
                    out.writeBoolean(userNameDisponivel);
                    out.flush();

                }

                case "alterarPassword" -> {

                    //Receber o username, a antiga pass e a nova
                    String username = in.readUTF();
                    String oldPasswordInput = in.readUTF();
                    String newpasswordInput = in.readUTF();

                    boolean passIgual = oldPasswordInput.equals(newpasswordInput);
                    boolean passCerta=false;

                    rs = statement.executeQuery("SELECT `password` FROM utilizador WHERE `username`='" + username + "';");
                    if(rs.getString(0).equals(oldPasswordInput)){
                        passCerta=true;
                    }

                    //Alterar a pass, update da db
                    if(!passIgual&&passCerta) {
                        rs = statement.executeQuery("UPDATE utilizador SET `password`='" + newpasswordInput + "' WHERE `username`='" + username + "';");
                    }

                    out.writeBoolean(passIgual);
                    out.writeBoolean(passCerta);
                    out.flush();

                }

                case "getLoja" -> {





                }

                default -> {
                }
            }


            out.close();
            in.close();


        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println("fbhuaigfyduf");

        
    }

    



    
}
