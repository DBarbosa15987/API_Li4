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
            this.c = DriverManager.getConnection("jdbc:mysql://192.168.0.107:3306/mydb", "afonso", "bomdia123");
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
                -> getCategorias OK

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
                    while(rs.next()) {

                        //Sinalizar que um favorito será enviado
                        out.writeBoolean(true);
                        out.writeUTF(rs.getString("idLoja"));
                    }

                    //Sinalizar que já não existem mais favoritos para receber
                    out.writeBoolean(false);

                    //Obter a informação dos votos
                    rs = statement.executeQuery("SELECT * FROM voto WHERE `utilizador_username`='" + username + "';");

                    //Enviar a informação dos votos
                    while(rs.next()){

                        //Sinalizar que um voto será enviado
                        out.writeBoolean(true);
                        out.writeUTF(rs.getString("categoria_nomeCategoria"));
                        out.writeUTF(rs.getString("loja_idloja"));
                        out.writeBoolean(rs.getBoolean("voto"));
                    }

                    //Sinalizar que já não existem mais votos para receber
                    out.writeBoolean(false);

                    //Obter a informação dos comentários
                    rs = statement.executeQuery("SELECT * FROM comentario WHERE `username`='" + username + "';");

                    //Enviar informação os comentários
                    while(rs.next()){

                        //Sinalizar que um comentário será enviado
                        out.writeBoolean(true);
                        out.writeUTF(rs.getString("idLoja"));
                        out.writeUTF(rs.getString("comentarioText"));
                        var timestamp = rs.getTimestamp("data");
                        out.writeLong(timestamp.getTime());

                    }

                    //Sinalizar que já não existem mais comentários para receber
                    out.writeBoolean(false);

                    out.flush();
                }

                //quando um user é auteticado apenas é enviado uma confirmação e o seu username
                case "autenticaUser" -> {

                    //Receber username e pass que o user introduz
                    String usernameInput = in.readUTF();
                    String passwordInput = in.readUTF();


                    rs = statement.executeQuery("SELECT `username` FROM utilizador WHERE `username`='" + usernameInput +"' AND `password`='" + passwordInput + "';");

                    //Quando o valor não existe da db, o ResultSet retorna 0 rows, e por isso rs.next() seria falso
                    boolean autenticado = rs.next();
                    out.writeBoolean(autenticado);
                    out.flush();

                    //Se o User for autenticado, enviar agora o username para manter em cache
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

                    //Quando o valor não existe da db, o ResultSet retorna 0 rows, e por isso rs.next() seria falso
                    //Username e email são únicos, por isso se não existirem na base de dados, estão disponíveis

                    //Verificar se o userName está disponível
                    rs = statement.executeQuery("SELECT * FROM utilizador WHERE `username`='" + usernameInput + "';");
                    boolean userNameDisponivel = !rs.next();
                    out.writeBoolean(userNameDisponivel);

                    //Verificar se o email está disponível
                    rs = statement.executeQuery("SELECT * FROM utilizador WHERE `email`='" + emailInput + "';");
                    boolean emailDisponivel = !rs.next();
                    out.writeBoolean(emailDisponivel);

                    //Credenciais confirmadas, inserir User na db
                    if(userNameDisponivel&&emailDisponivel) {
                        rs = statement.executeQuery("INSERT INTO utilizador VALUES (" + usernameInput +"," + emailInput + "," + passwordInput + "," + nomeCompletoInput + "," + moradaInput + ")");
                    }

                    out.flush();

                }

                case "checkUserName" -> {

                    //Receber username que o user introduz
                    String usernameInput = in.readUTF();

                    rs = statement.executeQuery("SELECT * FROM utilizador WHERE `username`='" + usernameInput + "';");

                    //Quando o valor não existe da db, o ResultSet retorna 0 rows, e por isso rs.next() seria falso
                    //Username é único, por isso se não existir na base de dados, está disponível
                    boolean userNameDisponivel = !rs.next();
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

                    //Dizer o que correu bem e o que não, se os dois são verdadeiros sabemos que a password foi atualizada
                    out.writeBoolean(passIgual);
                    out.writeBoolean(passCerta);
                    out.flush();

                }

                //(id, nome, local x y , horario a f)
                case "getLojasPreview" -> {

                    //o cliente calcula o dia
                    int hoje = in.readInt();

                    //Receber as informações da loja para a preview, incluido o horário de "hoje"
                    rs = statement.executeQuery("SELECT loja.idloja,loja.nome, loja.coordX,loja.coordY,horario.abertura,horario.fecho FROM loja INNER JOIN horario ON loja.idloja = horario.idLoja WHERE `diaSemana`="+hoje+";");

                    //Enviar o número de Lojas
                    while(rs.next()) {

                        //Sinalizar que uma LojaPreview será enviada
                        out.writeBoolean(true);
                        out.writeUTF(rs.getString("idLoja"));
                        out.writeUTF(rs.getString("nome"));
                        out.writeFloat(rs.getFloat("coordX"));
                        out.writeFloat(rs.getFloat("coordY"));

                        //Estes dois são passados como strings e levam parse no cliente
                        out.writeUTF(rs.getTime("abertura").toString());
                        out.writeUTF(rs.getTime("fecho").toString());
                    }

                    //Sinalizar que já não existem mais lojas para receber
                    out.writeBoolean(false);
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


                    //Estes dois 'times' são passados como strings e levam parse no cliente
                    while(rs.next()){

                        //Sinalizar que será enviado um horario
                        out.writeBoolean(true);
                        out.writeInt(rs.getInt("diaSemana"));
                        out.writeUTF(rs.getTime("abertura").toString());
                        out.writeUTF(rs.getTime("fecho").toString());
                    }

                    //Sinalizar que já não existem horarios para receber
                    out.writeBoolean(false);

                    //Receber as informações dos votos
                    rs = statement.executeQuery("SELECT * FROM voto WHERE `loja_idloja`='" + idLoja + "';");


                    while(rs.next()){

                        //Sinalizar que será enviado um voto
                        out.writeBoolean(true);
                        out.writeUTF(rs.getString("utilizador_username"));
                        out.writeUTF(rs.getString("categoria_nomeCategoria"));
                        out.writeBoolean(rs.getBoolean("voto"));
                    }

                    //Sinalizar que já não existem votos para receber
                    out.writeBoolean(false);

                    //Receber as informações dos comentários
                    rs = statement.executeQuery("SELECT * FROM comentario WHERE `idLoja`='" + idLoja + "';");


                    while(rs.next()){
                        //Sinalizar que será enviado um comentario
                        out.writeBoolean(true);
                        out.writeUTF(rs.getString("username"));
                        out.writeUTF(rs.getString("comentarioText"));
                        var timestamp = rs.getTimestamp("data");
                        out.writeLong(timestamp.getTime());
                    }

                    //Sinalizar que já não existem comentarios para receber
                    out.writeBoolean(false);

                    out.flush();
                }

                case "getCategorias" -> {

                    rs = statement.executeQuery("SELECT * FROM categoria;");


                    while(rs.next()){

                        //Sinalizar que uma categoria será enviada
                        out.writeBoolean(true);
                        out.writeUTF(rs.getString("nomeCategoria"));
                    }

                    //Sinalizar que já não existem mais categorias para receber
                    out.writeBoolean(false);

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
