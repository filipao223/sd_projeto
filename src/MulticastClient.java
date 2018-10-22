import java.net.MulticastSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MulticastClient -> classe que recebe os callbacks do servidor
 * MulticastUser -> classe que interage com o utlizador e envia os pedidos ao servidor
 * <p>
 * Ao carregar no main, a classe MulticastUser envia o primeiro pedido, do tipo CHECK_SERVER_UP, que pergunta a quaisquer
 * servidores multicast que estejam ligados os seus numeros, para os poder incluir no campo "server number" dos
 * pacotes UDP.
 * <p>
 * O MulticastClient receberá uma ou várias respostas, ou mesmo nenhuma, do servidor, que conterá o(s) numero(s) do(s)
 * servidore(s) ligados. Este número é adicionado a uma lista de inteiros partilhada em toda a classe ('serverNumbers')
 * Não é obrigatório esta resposta chegar assim que o MulticastClient se liga, se houver um novo servidor, ele enviará o seu número
 * automaticamente.
 * <p>
 * Voltando ao MulticastUser, enviado este pedido inicial, vai ficar à espera que o utilizador escreva o numero de uma feature que
 * pretenda usar (códigos em Request). Depois de todos os parametros serem preenchidos pelo user de acordo com a funcionalidade que quer,
 * o pedido é colocado num HashMap, e enviado para o servidor, a resposta sendo recebida pela classe MulticastClient
 */
public class MulticastClient extends Thread {
    private String MULTICAST_ADDRESS = "224.3.2.1";
    private int PORT = 4321;
    private static List<Integer> serverNumbers = new ArrayList<>(); //lista de numeros de servidores

    public static void main(String[] args) {
        MulticastClient client = new MulticastClient();
        client.start();
        MulticastUser user = new MulticastUser(serverNumbers);
        user.start();
    }

    @SuppressWarnings("unchecked")
    public void run() {
        MulticastSocket socket = null;
        ExecutorService executor = Executors.newFixedThreadPool(5); //Pool de threads
        try {
            socket = new MulticastSocket(PORT);  // create socket and bind it
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);
            while (true) {
                byte[] buffer = new byte[2048];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); //Recebe pacotes vindo dos servidores

                //Cria uma task, e coloca-a na pool de threads
                executor.submit(new DecodePacket(packet, serverNumbers));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            socket.close();
        }
    }
}

class MulticastUser extends Thread {
    private String MULTICAST_ADDRESS = "224.3.2.1";
    private int PORT = 4321;
    private List<Integer> serverNumbers;

    public MulticastUser(List<Integer> list) {
        super("User " + (long) (Math.random() * 1000));
        this.serverNumbers = list;
    }

    public void run() {
        // TODO (optional) tornar as coisas mais óbvias ao utlizador
        Serializer s = new Serializer();
        MulticastSocket socket = null;
        System.out.println(this.getName() + " ready...");
        try {
            socket = new MulticastSocket();  // create socket without binding it (only for sending)
            Scanner keyboardScanner = new Scanner(System.in);

            //Pedido inicial, coloca a feature CHECK_SERVER_UP, a qual o servidor vai responder
            //(ou nao) o seu numero
            Map<String, Object> checkServer = new HashMap<>();
            checkServer.put("feature", String.valueOf(Request.CHECK_SERVER_UP));

            byte[] buffer = s.serialize(checkServer);

            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
            socket.send(packet); //Envia

            while (true) {
                boolean alreadyGotFeatureCode = false;
                Map<String, Object> data = new HashMap<>();

                Random r = new Random(); //Objecto Random que vai gerar um indice, para escolher o servidor
                String readKeyboard = "";
                if (serverNumbers.isEmpty()){ //Se não houver servidores atualmente ligados
                    System.out.println("Feature?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    //Se ainda não existir nenhum servidor, continue while
                    if (serverNumbers.isEmpty()){
                        System.out.println("No servers available");
                        continue;
                    }
                    //Já há um servidor ligado
                    //Como ja foi perguntado ao utilizador que feature queria,
                    //este booleano previne que mais á frente a pergunta seja repetida
                    //Esta situação só acontece se não houver servidores
                    alreadyGotFeatureCode = true;
                }
                int index = r.nextInt(serverNumbers.size()); //è aqui que o obejcto random gera um indice da lista
                data.put("server", String.valueOf(serverNumbers.get(index))); //cabeçalho do pacote udp, que servidor vai tratar do pedido
                System.out.println("==========Server " + serverNumbers.get(index) + "============");

                //Ja foi perguntado que feature queria?
                if (!alreadyGotFeatureCode){
                    //Ainda não, pergunta então
                    System.out.println("Feature?: ");
                    readKeyboard = keyboardScanner.nextLine();
                }
                data.put("feature", readKeyboard); //cabecaçlho do pacote udp, a feature requerida
//================================================LOGIN=====================================================================================================
                if (readKeyboard.matches("1") || readKeyboard.matches("29")){
                    System.out.println("Username?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("username", readKeyboard);

                    System.out.println("Password?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("password", readKeyboard);
                }
//================================================LOGOUT===================================================================================================
                else if(readKeyboard.matches("14")){
                    System.out.println("Username?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("username", readKeyboard);
                }
//==============================================TORNAR ALGUEM EDITOR=======================================================================================
                else if(readKeyboard.matches("6")){
                    System.out.println("Username?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("editor", readKeyboard);

                    System.out.println("New editor?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("newEditor", readKeyboard);
                }
//=====================================EDITAR (ADICIONAR, ALTERAR E REMOVER)================================================================================
                else if(readKeyboard.matches("2")){
                    System.out.println("Username?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("username", readKeyboard);
                    System.out.println("Add, remove or edit?: ");
                    readKeyboard = keyboardScanner.nextLine();

                    if (readKeyboard.matches("add")){ //PRETENDE ADICIONAR
                        String action = "";
                        System.out.println("Album ,music or artist?: "); //quer editar um arista, um album ou uma musica?
                        readKeyboard = keyboardScanner.nextLine();
                        if (readKeyboard.matches("album")){
                            action = action.concat(String.valueOf(Request.ADD_ALBUM)+"_");
                        }
                        else if (readKeyboard.matches("artist")){
                            action = action.concat(String.valueOf(Request.ADD_ARTIST)+"_");
                        }
                        else if (readKeyboard.matches("music")){
                            action = action.concat(String.valueOf(Request.ADD_MUSIC)+"_");
                        }
                        else{
                            System.out.println("Bad token");
                            continue;
                        }
                        System.out.println("Name?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        action = action.concat(readKeyboard);
                        data.put("action", action);
                    }

                    else if (readKeyboard.matches("edit")){ //PRETENDE EDITAR
                        System.out.println("Which data type to edit?(music, album or artist): "); //Editar o quê?
                        readKeyboard = keyboardScanner.nextLine();
                        String action = "";

                        //Birth date needs to be checked for proper format
                        boolean isBirth = false;

                        if (readKeyboard.matches("music")){
                            action = action.concat(String.valueOf(Request.EDIT_MUSIC)+"_");
                            System.out.println("Which field to edit?(name,year,album,artist): ");
                            readKeyboard = keyboardScanner.nextLine();
                            if(readKeyboard.matches("name")){
                                action = action.concat(String.valueOf(Request.EDIT_NAME)+"_");
                            }
                            else if (readKeyboard.matches("year")){
                                action = action.concat(String.valueOf(Request.EDIT_YEAR)+"_");
                            }
                            else if (readKeyboard.matches("album")){
                                action = action.concat(String.valueOf(Request.EDIT_FIELD_ALBUMS)+"_");
                            }
                            else if (readKeyboard.matches("artist")){
                                action = action.concat(String.valueOf(Request.EDIT_FIELD_ARTIST)+"_");
                            }
                            else{
                                System.out.println("No attribute with that name");
                                continue;
                            }
                        }
                        else if (readKeyboard.matches("album")){
                            action = action.concat(String.valueOf(Request.EDIT_ALBUM)+"_");
                            System.out.println("Which field to edit?(name,year,artist,genre,description): ");
                            readKeyboard = keyboardScanner.nextLine();
                            if(readKeyboard.matches("name")){
                                action = action.concat(String.valueOf(Request.EDIT_NAME)+"_");
                            }
                            else if (readKeyboard.matches("year")){
                                action = action.concat(String.valueOf(Request.EDIT_YEAR)+"_");
                            }
                            else if (readKeyboard.matches("artist")){
                                action = action.concat(String.valueOf(Request.EDIT_FIELD_ARTIST)+"_");
                            }
                            else if (readKeyboard.matches("description")){
                                action = action.concat(String.valueOf(Request.EDIT_DESCRIPTION)+"_");
                            }
                            else if (readKeyboard.matches("genre")){
                                action = action.concat(String.valueOf(Request.EDIT_GENRE)+"_");
                            }
                            else{
                                System.out.println("No attribute with that name");
                                continue;
                            }
                        }
                        else if (readKeyboard.matches("artist")){
                            action = action.concat(String.valueOf(Request.EDIT_ARTIST)+"_");
                            System.out.println("Which field to edit?(name,birth,description): ");
                            readKeyboard = keyboardScanner.nextLine();
                            if(readKeyboard.matches("name")){
                                action = action.concat(String.valueOf(Request.EDIT_NAME)+"_");
                            }
                            else if (readKeyboard.matches("birth")){
                                action = action.concat(String.valueOf(Request.EDIT_BIRTH)+"_");
                                isBirth = true;
                            }
                            else if (readKeyboard.matches("description")){
                                action = action.concat(String.valueOf(Request.EDIT_DESCRIPTION)+"_");
                            }
                            else{
                                System.out.println("No attribute with that name");
                                continue;
                            }
                        }
                        else{
                            System.out.println("No type found");
                            continue;
                        }

                        System.out.println("Which item is to be edited?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        action = action.concat(readKeyboard+"_");
                        System.out.println("New value?: ");
                        readKeyboard = keyboardScanner.nextLine();

                        //Birth date needs to be checked for proper format
                        if (isBirth){ //
                            if (!readKeyboard.matches("^\\s*(3[01]|[12][0-9]|0?[1-9])-(1[012]|0?[1-9])-((?:19|20)\\d{2})\\s*$")){
                                System.out.println("Bad date format, should be d-m-yyyy");
                                continue;
                            }
                        }

                        action = action.concat(readKeyboard);
                        System.out.println("Produced action: " + action);
                        data.put("action", action);
                    }
                    else if (readKeyboard.matches("remove")){ //PRETENDE REMOVER
                        String action = "";
                        System.out.println("Album, music or artist?: "); //Remover o quê?
                        readKeyboard = keyboardScanner.nextLine();
                        if (readKeyboard.matches("album")){
                            action = action.concat(String.valueOf(Request.REMOVE_ALBUM)+"_");
                        }
                        else if (readKeyboard.matches("artist")){
                            action = action.concat(String.valueOf(Request.REMOVE_ARTIST)+"_");
                        }
                        else if (readKeyboard.matches("music")){
                            action = action.concat(String.valueOf(Request.REMOVE_MUSIC)+"_");
                        }
                        else{
                            System.out.println("Bad token");
                            continue;
                        }
                        System.out.println("Name?: "); //Nome do que quer remover
                        readKeyboard = keyboardScanner.nextLine();
                        action = action.concat(readKeyboard);
                        data.put("action", action);
                    }

                }
//===================================================PESQUISAR===========================================================================================
                else if(readKeyboard.matches("3")){ //
                    System.out.println("Username?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("username", readKeyboard);

                    String action = "";
                    // TODO test search feature

                    System.out.println("Search artist, music or album?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    if (readKeyboard.matches("artist")){
                        action = action.concat(String.valueOf(Request.SEARCH_ARTIST)+"_");
                        System.out.println("Artist name?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        action = action.concat(String.valueOf(Request.SEARCH_BY_NAME)+"_"+readKeyboard);
                    }
                    // TODO (optional) mudar a forma de construir a ação (não obrigar o user a escrever name_"nome"_genre_"jazz")
                    else if (readKeyboard.matches("album")){
                        action = action.concat(String.valueOf(Request.SEARCH_ALBUM)+"_");
                        System.out.println("Parameters to search?(at least one of these- name, artist, genre)" +
                                             "(format: name_\"value\"_genre_\"jazz\", for example): ");
                        readKeyboard = keyboardScanner.nextLine();
                        
                        //Check format
                        if (!readKeyboard.matches("([a-zA-Z]+(?:_[a-zA-Z]+)*)")){
                            System.out.println("Bad string format");
                            continue;
                        }

                        //Decode input
                        String[] tokens = readKeyboard.split("\\_");
                        for(int i=0; i<tokens.length; i+=2){
                            if(tokens[i].matches("name")){
                                action = action.concat(String.valueOf(Request.SEARCH_BY_NAME)+"_"+tokens[i+1]);
                            }
                            else if(tokens[i].matches("artist")){
                                action = action.concat(String.valueOf(Request.SEARCH_BY_ARTIST)+"_"+tokens[i+1]);
                            }
                            else if(tokens[i].matches("genre")){
                                action = action.concat(String.valueOf(Request.SEARCH_BY_GENRE)+"_"+tokens[i+1]);
                            }
                        }
                    }
                    else if (readKeyboard.matches("music")){
                        action = action.concat(String.valueOf(Request.SEARCH_MUSIC)+"_");
                        System.out.println("Parameters to search?(at least one of these- name, artist, album)" +
                                             "(format: name_\"value\"_album_\"album name\", for example): ");
                        readKeyboard = keyboardScanner.nextLine();
                        
                        //Check format
                        // TODO arranjar esta expressao regular para so aceitar numeros em cada valor par (1_2_3_4)
                        if (!readKeyboard.matches("([a-zA-Z0-9]+(?:_[a-zA-Z0-9]+)*)")){
                            System.out.println("Bad string format");
                            continue;
                        }

                        //Decode input
                        String[] tokens = readKeyboard.split("_");
                        for(int i=0; i<tokens.length; i+=2){
                            if(tokens[i].matches("name")){
                                action = action.concat(String.valueOf(Request.SEARCH_BY_NAME)+"_"+tokens[i+1]);
                            }
                            else if(tokens[i].matches("artist")){
                                action = action.concat(String.valueOf(Request.SEARCH_BY_ARTIST)+"_"+tokens[i+1]);
                            }
                            else if(tokens[i].matches("album")){
                                action = action.concat(String.valueOf(Request.SEARCH_BY_ALBUM)+"_"+tokens[i+1]);
                            }
                            if ((i+2)<tokens.length) action = action.concat("_");
                        }
                    }
                    else{
                        System.out.println("Bad token");
                        continue;
                    }

                    data.put("action", action);
                }
                buffer = s.serialize(data);

                group = InetAddress.getByName(MULTICAST_ADDRESS);
                packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                socket.send(packet);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            socket.close();
        }
    }
}

class DecodePacket implements Runnable{

    private static List<Integer> serverNumbers;
    private DatagramPacket packet;
    private Serializer s = new Serializer();

    DecodePacket(DatagramPacket packet, List<Integer> list){
        DecodePacket.serverNumbers = list;
        this.packet = packet;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run(){
        try{
            Map<String, Object> data = (Map<String, Object>) s.deserialize(packet.getData());

//=============================================DO TIPO CALLBACK=============================================================
            if (((String)data.get("feature")).matches("13")){
                // TODO mudar a maneira de verificar se esta a receber resultados de pesquisa
                System.out.println("------------Callback is: ");
                System.out.println("Feature: " + data.get("feature"));
                System.out.println("Username: " + data.get("username"));
                System.out.println("Resposta: " + data.get("answer"));
                if (((String)data.get("answer")).matches("Found results")){
                    String[] results = ((String)data.get("optional")).split("_");
                    for (String s:results){
                        System.out.println(s);
                    }
                }
                System.out.println("Opcional: " + data.get("optional"));
                System.out.println("-----------Done");
            }
//=============================================NOTIFICAÇÂO NOVO EDITOR=============================================================
            else if (((String)data.get("feature")).matches("7")){
                System.out.println("-----------New note: ");
                // TODO (optional) Mudar "user1" was made editor para "you" were made editor
                System.out.println(data.get("username") + " was made editor");
            }
//=============================================ENTREGA VARIAS NOTIFICAÇOES=============================================================
            //Quando o user volta a ficar online, leva com as notificaçoes todas
            else if (((String)data.get("feature")).matches("9")){
                System.out.println("-----------New notes for " + data.get("username") + ": ");
                String notes = (String) data.get("notes");
                for (String note:notes.split("\\|")){
                    System.out.println(note);
                }
                System.out.println("-----------Done");
            }
//=============================================RESPOSTAS INTERNAS=============================================================
            //O utilizador não recebe estas mensagens
            //Novo servidor ligado
            else if(((String)data.get("feature")).matches("30")){
                System.out.println("-----------New server (" + data.get("new_server") + ")------------");
                serverNumbers.add((int)data.get("new_server")); //Adiciona o numero do servidor à lista da classe
            }
            //Um servidor foi desligado
            else if(((String)data.get("feature")).matches("31")){
                System.out.println("-----------Server down (" + data.get("server_down") + ")------------");
                if (!serverNumbers.isEmpty()) serverNumbers.remove((int)data.get("server_down")); //Remove o numero do servidor da lista da classe
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
