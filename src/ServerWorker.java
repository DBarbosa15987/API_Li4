import netscape.javascript.JSObject;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.sql.*;
import java.time.LocalTime;
import java.util.*;


public class ServerWorker implements Runnable{

    
    private Socket s;
    private Connection c;
    
    public ServerWorker(Socket s) throws ClassNotFoundException{
        this.s=s;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.c = DriverManager.getConnection("jdbc:mysql://193.200.241.76:3306/mydb", "root", "trabalholi");
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

                --------USER--------
                -> autenticaUser OK
                -> criarConta OK
                -> checkUserName OK
                -> alterarPassword OK
                -> getComentarios OK
                -> getFavoritos OK
                -> alterVote OK
                -> comentar OK
                -> toggleFavorito OK

                --------LOJA--------
                -> getLoja (tudo) - apenas uma loja OK
                -> getLojaPreview (id, nome, local x y , horario a f) - todas as lojas OK
                -> getCategorias OK
             */


            switch (query) {

                case "autenticaUser" -> {

                    //Receber username e pass que o user introduz
                    String usernameInput = in.readUTF();
                    String passwordInput = in.readUTF();


                    rs = statement.executeQuery("SELECT `username` FROM utilizador WHERE `username`='" + usernameInput +"' AND `password`='" + passwordInput + "';");

                    //Quando o valor não existe da db, o ResultSet retorna 0 rows, e por isso rs.next() seria falso
                    boolean autenticado = rs.next();
                    out.writeBoolean(autenticado);
                    out.flush();

                    //Se está autenticado, envias todas as infos do user
                    if(autenticado){

                        rs = statement.executeQuery("SELECT * FROM utilizador WHERE `username`='" + usernameInput + "';");

                        rs.next();
                        out.writeUTF(rs.getString("nomeCompleto"));
                        out.writeUTF(rs.getString("morada"));
                        out.writeUTF(rs.getString("email"));
                        String profilePic = rs.getString("pfpURL");
                        boolean pic = profilePic!=null;

                        //informar se o user tem foto de perfil ou não
                        out.writeBoolean(pic);
                        if(pic) {
                            out.writeUTF(profilePic);
                        }
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
                        var queryStat = c.prepareStatement("INSERT INTO utilizador VALUES ('" + usernameInput +"','" + emailInput + "','" + passwordInput + "','" + nomeCompletoInput + "','" + moradaInput + "');");
                        queryStat.executeUpdate();
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
                    rs.next();
                    if(rs.getString(0).equals(oldPasswordInput)){
                        passCerta=true;
                    }

                    //Alterar a pass, update da db
                    if(!passIgual&&passCerta) {
                        var queryStat = c.prepareStatement("UPDATE utilizador SET `password`='" + newpasswordInput + "' WHERE `username`='" + username + "';");
                        queryStat.executeUpdate();
                    }

                    //Dizer o que correu bem e o que não, se os dois são verdadeiros sabemos que a password foi atualizada
                    out.writeBoolean(passIgual);
                    out.writeBoolean(passCerta);
                    out.flush();

                }

                case "getFavoritos" -> {

                    String username = in.readUTF();
                    int hoje = in.readInt();

                    //Obter a informação dos favoritos
                    rs = statement.executeQuery("SELECT * FROM favorito WHERE `username`='" + username + "';");

                    Set<String> favoritos = new HashSet<>();

                    //Popular o Set com os IDs das lojas favoritas
                    while(rs.next()) {
                        favoritos.add(rs.getString("idLoja"));
                    }

                    //Os favoritos vão ser apresentados da mesma maneira que as lojasPreview!
                    //Obter as informações da Loja que constam na lista de favoritos do user
                    for(String idLoja : favoritos) {
                        rs = statement.executeQuery("SELECT * FROM loja INNER JOIN horario ON loja.idloja = horario.idLoja WHERE `diaSemana`=" + hoje + " AND loja.idloja='" + idLoja + "';");

                        //Sinalizar que uma LojaPreview será enviada
                        rs.next();
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

                case "getComentarios" -> {

                    String username = in.readUTF();

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

                case "alterVote" -> {

                    /*
                    'alter' pode ser:
                        upvote (1)
                        downvote (-1)
                        remove (0)
                     */

                    int alter = in.readInt();
                    String usernameInput = in.readUTF();
                    String idLojaInput = in.readUTF();
                    String categoriaInput = in.readUTF();

                    switch (alter){

                        //upvote
                        case 1 ->
                                c.prepareStatement("INSERT INTO voto VALUES ('" + usernameInput + "','" + categoriaInput + "','" + idLojaInput + "','" + 1 + "');").executeUpdate();

                        //remove
                        case 0 ->
                                c.prepareStatement("DELETE FROM voto WHERE `utilizador_username`='" + usernameInput + "' AND `loja_idloja`='" + idLojaInput + "';").executeUpdate();

                        //downvote
                        case -1 ->
                                c.prepareStatement("INSERT INTO voto VALUES ('" + usernameInput + "','" + categoriaInput + "','" + idLojaInput + "','" + 0 + "');").executeUpdate();

                    }

                }

                case "comentar" -> {

                    String usernameInput = in.readUTF();
                    String idLojaInput = in.readUTF();
                    String comentarioInput = in.readUTF();
                    long data = in.readLong();
                    Timestamp timestamp = new Timestamp(data);

                    var queryStat = c.prepareStatement("INSERT INTO comentario VALUES ('" + usernameInput + "','" + idLojaInput + "','" + comentarioInput + "','" + timestamp + "');");
                    queryStat.executeUpdate();
                }

                case "toggleFavorito" -> {

                    boolean addOrRemove = in.readBoolean();
                    String usernameInput = in.readUTF();
                    String idLojaInput = in.readUTF();

                    if(addOrRemove) {
                        //add
                        var queryStat = c.prepareStatement("INSERT INTO favorito VALUES ('" + usernameInput + "','" + idLojaInput + "');");
                        queryStat.executeUpdate();
                    }
                    else{
                        //remove
                        var queryStat = c.prepareStatement("DELETE FROM favorito WHERE `username`='" + usernameInput + "' AND `idLoja`='" + idLojaInput + "';");
                        queryStat.executeUpdate();
                    }
                }

                case "getLojasPreview" -> {

                    //o cliente calcula o dia
                    String username = in.readUTF();
                    int hoje = in.readInt();

                    //Receber as informações da loja para a preview, incluido o horário de "hoje"
                    rs = statement.executeQuery("SELECT loja.idloja,loja.nome, loja.coordX,loja.coordY,horario.abertura,horario.fecho FROM loja " +
                                                    "LEFT JOIN horario ON loja.idloja = horario.idLoja " +
                                                    "WHERE `diaSemana`=" + hoje + ";");

                    //Outra query
                    ResultSet rs2;
                    java.sql.Statement statement2 = c.createStatement();

                    //Enviar a informação das lojas
                    while(rs.next()) {

                        //Sinalizar que uma LojaPreview será enviada
                        out.writeBoolean(true);
                        String idLoja = rs.getString("idLoja");
                        out.writeUTF(idLoja);
                        out.writeUTF(rs.getString("nome"));
                        out.writeFloat(rs.getFloat("coordX"));
                        out.writeFloat(rs.getFloat("coordY"));

                        //Estes dois são passados como strings e levam parse no cliente
                        out.writeUTF(rs.getTime("abertura").toString());
                        out.writeUTF(rs.getTime("fecho").toString());

                        rs2 = statement2.executeQuery("SELECT * FROM favorito WHERE `idLoja`='" + idLoja + "' AND `username`='" + username + "';");

                        //Se existe next, quer dizer que o request não veio vazio e esta loja é um favorito deste user
                        boolean favorito = rs2.next();
                        out.writeBoolean(favorito);

                        //Obter os votos desta loja para saber a que categorias pertence
                        rs2 = statement2.executeQuery("SELECT * FROM voto WHERE `loja_idloja`='" + idLoja + "';");

                        //Map<categoria,n_votos>
                        Map<String,Integer> votos = new HashMap<>();

                        //Popular o mapa para organizar os votos e as respetivas categorias
                        while(rs2.next()){

                            String categoria = rs2.getString("categoria_nomeCategoria");
                            boolean voto = rs2.getBoolean("voto");

                            if(votos.containsKey(categoria)){

                                var v = votos.get(categoria);

                                if(voto) {
                                    v++;
                                }
                                else{
                                    v--;
                                }

                            }
                            else{
                                int v = voto ? 1 : -1;
                                votos.put(categoria,v);
                            }

                        }

                        //Iterar o mapa populado e enviar as categorias válidas da Loja
                        for(var e : votos.entrySet()){

                            if(e.getValue()>0){

                                //Informar ao cliente que vai ser enviada uma categoria da Loja
                                out.writeBoolean(true);
                                out.writeUTF(e.getKey());

                            }

                        }

                        //Esta loja já não tem mais categorias
                        out.writeBoolean(false);

                    }

                    //Sinalizar que já não existem mais lojas para receber
                    out.writeBoolean(false);
                    out.flush();

                }

                case "getLoja" -> {

                    String username = in.readUTF();
                    String idLoja = in.readUTF();

                    //Receber as informações da Loja
                    rs = statement.executeQuery("SELECT * FROM loja WHERE loja.idloja = '" + idLoja + "';");


                    //Informação da loja
                    rs.next();
                    out.writeUTF(rs.getString("nome"));
                    out.writeUTF(rs.getString("website"));
                    out.writeUTF(rs.getString("email"));
                    out.writeUTF(rs.getString("telefone"));
                    out.writeFloat(rs.getFloat("coordX"));
                    out.writeFloat(rs.getFloat("coordY"));

                    //Check se é favorito
                    rs = statement.executeQuery("SELECT * FROM favorito WHERE `idLoja`='" + idLoja + "' AND `username`='" + username + "';");

                    //Se existe next, quer dizer que o request não veio vazio e esta loja é um favorito deste user
                    boolean favorito = rs.next();
                    out.writeBoolean(favorito);

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

                    //Receber as informações dos votos todos da loja
                    rs = statement.executeQuery("SELECT * FROM voto WHERE `loja_idloja`='" + idLoja + "';");

                    //Map<categoria,n_votos>
                    Map<String,Integer> votos = new HashMap<>();

                    //Popular o mapa para organizar os votos e as respetivas categorias
                    while(rs.next()){

                        String categoria = rs.getString("categoria_nomeCategoria");
                        boolean voto = rs.getBoolean("voto");

                        if(votos.containsKey(categoria)){

                            var v = votos.get(categoria);

                            if(voto) {
                                v++;
                            }
                            else{
                                v--;
                            }

                        }
                        else{
                            int v = voto ? 1 : -1;
                            votos.put(categoria,v);
                        }

                    }

                    //Como é que o user votou em cada categoria (pode não conter todas as possíveis categorias)
                    Map<String,Integer> votosUser = new HashMap<>();

                    //Receber as informações dos votos que o user fez
                    rs = statement.executeQuery("SELECT * FROM voto WHERE `loja_idloja`='" + idLoja + "' AND `utilizador_username`='" + username + "';");

                    //Neste ciclo assume-se que a base de dados está populada corretamente e cada user tem apenas um voto em cada categoria numa determinada Loja
                    while(rs.next()){

                        String categoria = rs.getString("categoria_nomeCategoria");
                        boolean voto = rs.getBoolean("voto");
                        int v = voto ? 1 : -1;
                        votosUser.put(categoria,v);

                    }

                    //O mapa é agora iterado para enviar a informação ao cliente
                    for(var set : votos.entrySet()){

                        String key = set.getKey();

                        var v = votosUser.get(key);
                        int votoUser = v==null ? 0 : v;

                        //Sinalizar que será enviado um voto
                        out.writeBoolean(true);
                        out.writeUTF(key);
                        out.writeInt(set.getValue());
                        out.writeInt(votoUser);

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

                case "removeComent" -> {

                    String usernameInput = in.readUTF();
                    String idLojaInput = in.readUTF();

                    //Aqui considera-se que cada utilizador apenas pode fazer um comentário por loja
                    var queryStat = c.prepareStatement("DELETE FROM comentario WHERE `username`='" + usernameInput + "' AND `idLoja`='" + idLojaInput + "';");
                    queryStat.executeUpdate();
                }

                case "alterarPfp" -> {

                    String username = in.readUTF();
                    int size = in.readInt();
                    byte[] arr = new byte[size];
                    String extension = in.readUTF();
                    String linkDir = "/var/www/html/user_" + username + "." + extension;
                    in.read(arr,0,arr.length);
                    File file = new File(linkDir);
                    OutputStream os = new FileOutputStream(file);
                    os.write(arr);

//                    FileOutputStream fos = new FileOutputStream(linkDir);
//                    BufferedOutputStream bos = new BufferedOutputStream(fos);
//                    int bytesRead = this.s.getInputStream().read(arr,0,arr.length);

                    String link = "http://193.200.241.76:9080/user_" + username + "." + extension;
                    out.writeUTF(link);
                    os.flush();
                    var queryStat = c.prepareStatement("UPDATE utilizador SET `pfpUrl`='" + link + "' WHERE `username`='" + username + "';");
                    queryStat.executeUpdate();
                    out.flush();

                }

            }

            System.out.println("Conexão terminada na porta " + s.getPort());

            out.close();
            in.close();
            s.close();

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
    
}
