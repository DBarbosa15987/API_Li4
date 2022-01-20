import netscape.javascript.JSObject;

import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.time.LocalTime;


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

                -> getUser (tudo) OK
                -> autenticaUser OK
                -> criarConta OK
                -> checkUserName OK
                -> alterarPassword OK
                -> getLoja (tudo) - apenas uma loja OK
                -> getLojaPreview (id, nome, local x y , horario a f) - todas as lojas OK
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

                //(id, nome, local x y , horario a f)
                case "getLojaPreview" -> {

                    //o cliente calcula o dia
                    int hoje = in.readInt();

                    //Receber as informações da loja para a preview, incluido o horário de "hoje"
                    rs = statement.executeQuery("SELECT loja.idloja,loja.nome, loja.coordX,loja.coordY,horario.abertura,horario.fecho FROM loja INNER JOIN horario ON loja.idloja = horario.idLoja WHERE `diaSemana`=" + hoje);

                    //Enviar o número de Lojas
                    out.writeInt(rs.getFetchSize());

                    while(rs.next()) {
                        out.writeUTF(rs.getString("idLoja"));
                        out.writeUTF(rs.getString("nome"));
                        out.writeFloat(rs.getFloat("coordX"));
                        out.writeFloat(rs.getFloat("coordY"));

                        //Estes dois são passados como strings e levam parse no cliente
                        out.writeUTF(rs.getTime("abertura").toString());
                        out.writeUTF(rs.getTime("fecho").toString());
                    }

                    out.flush();

                }

                //Isto só acontece quando carregas numa preview de um café, por isso é feito apenas um de cada vez e o id da Loja já é conhecido
                case "getLoja" -> {

                    String idLoja = in.readUTF();

                    //Receber as informações da Loja
                    rs = statement.executeQuery("SELECT * FROM loja WHERE loja.idloja = '" + idLoja + "';");


                    //Informação da loja
                    out.writeUTF(rs.getString("idLoja"));
                    out.writeUTF(rs.getString("nome"));
                    out.writeUTF(rs.getString("website"));
                    out.writeUTF(rs.getString("email"));
                    out.writeUTF(rs.getString("telefone"));
                    out.writeFloat(rs.getFloat("coordX"));
                    out.writeFloat(rs.getFloat("coordY"));

                    //Receber as informações dos horários
                    rs = statement.executeQuery("SELECT * FROM horario WHERE idloja = '" + idLoja + "';");

                    //Devia ser 7, mas depende da implementação, não sei se quando a loja tiver
                    //fechada a entrada do horario vai ser nula ou simplesmente inexistente
                    out.writeInt(rs.getFetchSize());


                    //Estes dois são passados como strings e levam parse no cliente
                    while(rs.next()){
                        out.writeInt(rs.getInt("diaSemana"));
                        out.writeUTF(rs.getTime("abertura").toString());
                        out.writeUTF(rs.getTime("fecho").toString());
                    }


                    //Receber as informações dos votos
                    rs = statement.executeQuery("SELECT * FROM voto WHERE `loja_idloja`='" + idLoja + "';");

                    //Número de votos nesta Loja
                    out.writeInt(rs.getFetchSize());

                    while(rs.next()){
                        out.writeUTF(rs.getString("utilizador_username"));
                        out.writeUTF(rs.getString("categoria_nomeCategoria"));
                        out.writeBoolean(rs.getBoolean("voto"));
                    }

                    //Receber as informações dos comentários
                    rs = statement.executeQuery("SELECT * FROM comentario WHERE `idLoja`='" + idLoja + "';");

                    //Número de comentários nesta Loja
                    out.writeInt(rs.getFetchSize());

                    while(rs.next()){
                        out.writeUTF(rs.getString("username"));
                        out.writeUTF(rs.getString("comentarioText"));
                        var timestamp = rs.getTimestamp("data");
                        out.writeLong(timestamp.getTime());
                    }


                    out.flush();
                }

                case "getCategorias" -> {

                    rs = statement.executeQuery("SELECT * FROM categoria;");

                    //Número de comentários
                    out.writeInt(rs.getFetchSize());

                    while(rs.next()){
                        out.writeUTF(rs.getString("nomeCategoria"));
                    }

                    out.flush();

                }

            }

            System.out.println("Conexão terminada");

            out.close();
            in.close();
            s.close();

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        
    }

    



    
}
