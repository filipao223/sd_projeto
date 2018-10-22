import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.util.*;
import java.rmi.server.*;
import java.io.*;

public class RMIClient extends UnicastRemoteObject implements Client {

    static String name = null;

    public RMIClient() throws RemoteException {
        super();
    }

    public void print_on_client(Map<String, Object> data) throws RemoteException {

        /*Set set = h.entrySet();
		Iterator iterator = set.iterator();
		while(iterator.hasNext()) {
            Map.Entry mentry = (Map.Entry) iterator.next();
            System.out.print("key is: " + mentry.getKey() + " & Value is: ");
            System.out.println(mentry.getValue());
        }*/


//============================================NNEW=DO TIPO CALLBACK=============================================================
        if (((String)data.get("feature")).matches("13")){
            // TODO mudar a maneira de verificar se esta a receber resultados de pesquisa
            System.out.println("------------RMI SERVER Callback is: ");
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
    }

    public String getName() throws RemoteException{
        return name;
    }


    public static void remake() throws RemoteException {
        long time = System.currentTimeMillis();
        while (System.currentTimeMillis() < time + 30000) {
            try {

                Server h = (Server) LocateRegistry.getRegistry(1099).lookup("MainServer");

                RMIClient c = new RMIClient();

                Scanner keyboardScanner = new Scanner(System.in);

                while(true) {

                    boolean alreadyGotFeatureCode = false;
                    HashMap<String, Object> data = new HashMap<>();

                    String readKeyboard = "";

                    if (!alreadyGotFeatureCode) {
                        //Ainda não, pergunta então
                        System.out.println("Feature?: ");
                        readKeyboard = keyboardScanner.nextLine();
                    }
                    data.put("feature", readKeyboard); //cabecaçlho do pacote udp, a feature requerida
//================================================LOGIN=====================================================================================================
                    if (readKeyboard.matches("1") || readKeyboard.matches("29")) {
                        System.out.println("Username?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        data.put("username", readKeyboard);

                        name = readKeyboard;
                        h.subscribe(name,(Client) c);

                        System.out.println("Password?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        data.put("password", readKeyboard);

                    }
//================================================LOGOUT===================================================================================================
                    else if (readKeyboard.matches("14")) {
                        System.out.println("Username?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        data.put("username", readKeyboard);
                    }
//==============================================TORNAR ALGUEM EDITOR=======================================================================================
                    else if (readKeyboard.matches("6")) {
                        System.out.println("Username?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        data.put("editor", readKeyboard);

                        System.out.println("New editor?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        data.put("newEditor", readKeyboard);
                    }
//=====================================EDITAR (ADICIONAR, ALTERAR E REMOVER)================================================================================
                    else if (readKeyboard.matches("2")) {
                        System.out.println("Username?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        data.put("username", readKeyboard);
                        System.out.println("Add, remove or edit?: ");
                        readKeyboard = keyboardScanner.nextLine();

                        if (readKeyboard.matches("add")) { //PRETENDE ADICIONAR
                            String action = "";
                            System.out.println("Album ,music or artist?: "); //quer editar um arista, um album ou uma musica?
                            readKeyboard = keyboardScanner.nextLine();
                            if (readKeyboard.matches("album")) {
                                action = action.concat(String.valueOf(Request.ADD_ALBUM) + "_");
                            } else if (readKeyboard.matches("artist")) {
                                action = action.concat(String.valueOf(Request.ADD_ARTIST) + "_");
                            } else if (readKeyboard.matches("music")) {
                                action = action.concat(String.valueOf(Request.ADD_MUSIC) + "_");
                            } else {
                                System.out.println("Bad token");
                                continue;
                            }
                            System.out.println("Name?: ");
                            readKeyboard = keyboardScanner.nextLine();
                            action = action.concat(readKeyboard);
                            data.put("action", action);
                        } else if (readKeyboard.matches("edit")) { //PRETENDE EDITAR
                            System.out.println("Which data type to edit?(music, album or artist): "); //Editar o quê?
                            readKeyboard = keyboardScanner.nextLine();
                            String action = "";

                            //Birth date needs to be checked for proper format
                            boolean isBirth = false;

                            if (readKeyboard.matches("music")) {
                                action = action.concat(String.valueOf(Request.EDIT_MUSIC) + "_");
                                System.out.println("Which field to edit?(name,year,album,artist): ");
                                readKeyboard = keyboardScanner.nextLine();
                                if (readKeyboard.matches("name")) {
                                    action = action.concat(String.valueOf(Request.EDIT_NAME) + "_");
                                } else if (readKeyboard.matches("year")) {
                                    action = action.concat(String.valueOf(Request.EDIT_YEAR) + "_");
                                } else if (readKeyboard.matches("album")) {
                                    action = action.concat(String.valueOf(Request.EDIT_FIELD_ALBUMS) + "_");
                                } else if (readKeyboard.matches("artist")) {
                                    action = action.concat(String.valueOf(Request.EDIT_FIELD_ARTIST) + "_");
                                } else {
                                    System.out.println("No attribute with that name");
                                    continue;
                                }
                            } else if (readKeyboard.matches("album")) {
                                action = action.concat(String.valueOf(Request.EDIT_ALBUM) + "_");
                                System.out.println("Which field to edit?(name,year,artist,genre,description): ");
                                readKeyboard = keyboardScanner.nextLine();
                                if (readKeyboard.matches("name")) {
                                    action = action.concat(String.valueOf(Request.EDIT_NAME) + "_");
                                } else if (readKeyboard.matches("year")) {
                                    action = action.concat(String.valueOf(Request.EDIT_YEAR) + "_");
                                } else if (readKeyboard.matches("artist")) {
                                    action = action.concat(String.valueOf(Request.EDIT_FIELD_ARTIST) + "_");
                                } else if (readKeyboard.matches("description")) {
                                    action = action.concat(String.valueOf(Request.EDIT_DESCRIPTION) + "_");
                                } else if (readKeyboard.matches("genre")) {
                                    action = action.concat(String.valueOf(Request.EDIT_GENRE) + "_");
                                } else {
                                    System.out.println("No attribute with that name");
                                    continue;
                                }
                            } else if (readKeyboard.matches("artist")) {
                                action = action.concat(String.valueOf(Request.EDIT_ARTIST) + "_");
                                System.out.println("Which field to edit?(name,birth,description): ");
                                readKeyboard = keyboardScanner.nextLine();
                                if (readKeyboard.matches("name")) {
                                    action = action.concat(String.valueOf(Request.EDIT_NAME) + "_");
                                } else if (readKeyboard.matches("birth")) {
                                    action = action.concat(String.valueOf(Request.EDIT_BIRTH) + "_");
                                    isBirth = true;
                                } else if (readKeyboard.matches("description")) {
                                    action = action.concat(String.valueOf(Request.EDIT_DESCRIPTION) + "_");
                                } else {
                                    System.out.println("No attribute with that name");
                                    continue;
                                }
                            } else {
                                System.out.println("No type found");
                                continue;
                            }

                            System.out.println("Which item is to be edited?: ");
                            readKeyboard = keyboardScanner.nextLine();
                            action = action.concat(readKeyboard + "_");
                            System.out.println("New value?: ");
                            readKeyboard = keyboardScanner.nextLine();

                            //Birth date needs to be checked for proper format
                            if (isBirth) {
                                if (!readKeyboard.matches("^\\s*(3[01]|[12][0-9]|0?[1-9])-(1[012]|0?[1-9])-((?:19|20)\\d{2})\\s*$")) {
                                    System.out.println("Bad date format, should be d-m-yyyy");
                                    continue;
                                }
                            }

                            action = action.concat(readKeyboard);
                            System.out.println("Produced action: " + action);
                            data.put("action", action);
                        } else if (readKeyboard.matches("remove")) { //PRETENDE REMOVER
                            String action = "";
                            System.out.println("Album, music or artist?: "); //Remover o quê?
                            readKeyboard = keyboardScanner.nextLine();
                            if (readKeyboard.matches("album")) {
                                action = action.concat(String.valueOf(Request.REMOVE_ALBUM) + "_");
                            } else if (readKeyboard.matches("artist")) {
                                action = action.concat(String.valueOf(Request.REMOVE_ARTIST) + "_");
                            } else if (readKeyboard.matches("music")) {
                                action = action.concat(String.valueOf(Request.REMOVE_MUSIC) + "_");
                            } else {
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
                    else if (readKeyboard.matches("3")) { //
                        System.out.println("Username?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        data.put("username", readKeyboard);

                        String action = "";
                        // TODO test search feature

                        System.out.println("Search artist, music or album?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        if (readKeyboard.matches("artist")) {
                            action = action.concat(String.valueOf(Request.SEARCH_ARTIST) + "_");
                            System.out.println("Artist name?: ");
                            readKeyboard = keyboardScanner.nextLine();
                            action = action.concat(String.valueOf(Request.SEARCH_BY_NAME) + "_" + readKeyboard);
                        }
                        // TODO (optional) mudar a forma de construir a ação (não obrigar o user a escrever name_"nome"_genre_"jazz")
                        else if (readKeyboard.matches("album")) {
                            action = action.concat(String.valueOf(Request.SEARCH_ALBUM) + "_");
                            System.out.println("Parameters to search?(at least one of these- name, artist, genre)" +
                                    "(format: name_\"value\"_genre_\"jazz\", for example): ");
                            readKeyboard = keyboardScanner.nextLine();

                            //Check format
                            if (!readKeyboard.matches("([a-zA-Z]+(?:_[a-zA-Z]+)*)")) {
                                System.out.println("Bad string format");
                                continue;
                            }

                            //Decode input
                            String[] tokens = readKeyboard.split("\\_");
                            for (int i = 0; i < tokens.length; i += 2) {
                                if (tokens[i].matches("name")) {
                                    action = action.concat(String.valueOf(Request.SEARCH_BY_NAME) + "_" + tokens[i + 1]);
                                } else if (tokens[i].matches("artist")) {
                                    action = action.concat(String.valueOf(Request.SEARCH_BY_ARTIST) + "_" + tokens[i + 1]);
                                } else if (tokens[i].matches("genre")) {
                                    action = action.concat(String.valueOf(Request.SEARCH_BY_GENRE) + "_" + tokens[i + 1]);
                                }
                            }
                        } else if (readKeyboard.matches("music")) {
                            action = action.concat(String.valueOf(Request.SEARCH_MUSIC) + "_");
                            System.out.println("Parameters to search?(at least one of these- name, artist, album)" +
                                    "(format: name_\"value\"_album_\"album name\", for example): ");
                            readKeyboard = keyboardScanner.nextLine();

                            //Check format
                            // TODO arranjar esta expressao regular para so aceitar numeros em cada valor par (1_2_3_4)
                            if (!readKeyboard.matches("([a-zA-Z0-9]+(?:_[a-zA-Z0-9]+)*)")) {
                                System.out.println("Bad string format");
                                continue;
                            }

                            //Decode input
                            String[] tokens = readKeyboard.split("_");
                            for (int i = 0; i < tokens.length; i += 2) {
                                if (tokens[i].matches("name")) {
                                    action = action.concat(String.valueOf(Request.SEARCH_BY_NAME) + "_" + tokens[i + 1]);
                                } else if (tokens[i].matches("artist")) {
                                    action = action.concat(String.valueOf(Request.SEARCH_BY_ARTIST) + "_" + tokens[i + 1]);
                                } else if (tokens[i].matches("album")) {
                                    action = action.concat(String.valueOf(Request.SEARCH_BY_ALBUM) + "_" + tokens[i + 1]);
                                }
                                if ((i + 2) < tokens.length) action = action.concat("_");
                            }
                        } else {
                            System.out.println("Bad token");
                            continue;
                        }

                        data.put("action", action);
                    }
                    h.receive(data);
                }
                } catch (ConnectException e) {
                    System.out.println("A procura de conecao");
                } catch (NotBoundException e) {
                    System.out.println("A procura de conecao");
                }
            if(System.currentTimeMillis() >= time + 30000){
                    System.out.println("Não existe coneção");
                    break;
                }
        }
    }

    public static void main(String[] args){
        //Codes example
        try {

            Server h = (Server) LocateRegistry.getRegistry(1099).lookup("MainServer");

            RMIClient c = new RMIClient();

            Scanner keyboardScanner = new Scanner(System.in);

            while(true) {

                boolean alreadyGotFeatureCode = false;
                HashMap<String, Object> data = new HashMap<>();

                String readKeyboard = "";

                if (!alreadyGotFeatureCode) {
                    //Ainda não, pergunta então
                    System.out.println("Feature?: ");
                    readKeyboard = keyboardScanner.nextLine();
                }
                data.put("feature", readKeyboard); //cabecaçlho do pacote udp, a feature requerida
//================================================LOGIN=====================================================================================================
                if (readKeyboard.matches("1") || readKeyboard.matches("29")) {
                    System.out.println("Username?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("username", readKeyboard);

                    name = readKeyboard;
                    h.subscribe(name,(Client) c);

                    System.out.println("Password?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("password", readKeyboard);

                }
//================================================LOGOUT===================================================================================================
                else if (readKeyboard.matches("14")) {
                    System.out.println("Username?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("username", readKeyboard);
                }
//==============================================TORNAR ALGUEM EDITOR=======================================================================================
                else if (readKeyboard.matches("6")) {
                    System.out.println("Username?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("editor", readKeyboard);

                    System.out.println("New editor?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("newEditor", readKeyboard);
                }
//=====================================EDITAR (ADICIONAR, ALTERAR E REMOVER)================================================================================
                else if (readKeyboard.matches("2")) {
                    System.out.println("Username?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("username", readKeyboard);
                    System.out.println("Add, remove or edit?: ");
                    readKeyboard = keyboardScanner.nextLine();

                    if (readKeyboard.matches("add")) { //PRETENDE ADICIONAR
                        String action = "";
                        System.out.println("Album ,music or artist?: "); //quer editar um arista, um album ou uma musica?
                        readKeyboard = keyboardScanner.nextLine();
                        if (readKeyboard.matches("album")) {
                            action = action.concat(String.valueOf(Request.ADD_ALBUM) + "_");
                        } else if (readKeyboard.matches("artist")) {
                            action = action.concat(String.valueOf(Request.ADD_ARTIST) + "_");
                        } else if (readKeyboard.matches("music")) {
                            action = action.concat(String.valueOf(Request.ADD_MUSIC) + "_");
                        } else {
                            System.out.println("Bad token");
                            continue;
                        }
                        System.out.println("Name?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        action = action.concat(readKeyboard);
                        data.put("action", action);
                    } else if (readKeyboard.matches("edit")) { //PRETENDE EDITAR
                        System.out.println("Which data type to edit?(music, album or artist): "); //Editar o quê?
                        readKeyboard = keyboardScanner.nextLine();
                        String action = "";

                        //Birth date needs to be checked for proper format
                        boolean isBirth = false;

                        if (readKeyboard.matches("music")) {
                            action = action.concat(String.valueOf(Request.EDIT_MUSIC) + "_");
                            System.out.println("Which field to edit?(name,year,album,artist): ");
                            readKeyboard = keyboardScanner.nextLine();
                            if (readKeyboard.matches("name")) {
                                action = action.concat(String.valueOf(Request.EDIT_NAME) + "_");
                            } else if (readKeyboard.matches("year")) {
                                action = action.concat(String.valueOf(Request.EDIT_YEAR) + "_");
                            } else if (readKeyboard.matches("album")) {
                                action = action.concat(String.valueOf(Request.EDIT_FIELD_ALBUMS) + "_");
                            } else if (readKeyboard.matches("artist")) {
                                action = action.concat(String.valueOf(Request.EDIT_FIELD_ARTIST) + "_");
                            } else {
                                System.out.println("No attribute with that name");
                                continue;
                            }
                        } else if (readKeyboard.matches("album")) {
                            action = action.concat(String.valueOf(Request.EDIT_ALBUM) + "_");
                            System.out.println("Which field to edit?(name,year,artist,genre,description): ");
                            readKeyboard = keyboardScanner.nextLine();
                            if (readKeyboard.matches("name")) {
                                action = action.concat(String.valueOf(Request.EDIT_NAME) + "_");
                            } else if (readKeyboard.matches("year")) {
                                action = action.concat(String.valueOf(Request.EDIT_YEAR) + "_");
                            } else if (readKeyboard.matches("artist")) {
                                action = action.concat(String.valueOf(Request.EDIT_FIELD_ARTIST) + "_");
                            } else if (readKeyboard.matches("description")) {
                                action = action.concat(String.valueOf(Request.EDIT_DESCRIPTION) + "_");
                            } else if (readKeyboard.matches("genre")) {
                                action = action.concat(String.valueOf(Request.EDIT_GENRE) + "_");
                            } else {
                                System.out.println("No attribute with that name");
                                continue;
                            }
                        } else if (readKeyboard.matches("artist")) {
                            action = action.concat(String.valueOf(Request.EDIT_ARTIST) + "_");
                            System.out.println("Which field to edit?(name,birth,description): ");
                            readKeyboard = keyboardScanner.nextLine();
                            if (readKeyboard.matches("name")) {
                                action = action.concat(String.valueOf(Request.EDIT_NAME) + "_");
                            } else if (readKeyboard.matches("birth")) {
                                action = action.concat(String.valueOf(Request.EDIT_BIRTH) + "_");
                                isBirth = true;
                            } else if (readKeyboard.matches("description")) {
                                action = action.concat(String.valueOf(Request.EDIT_DESCRIPTION) + "_");
                            } else {
                                System.out.println("No attribute with that name");
                                continue;
                            }
                        } else {
                            System.out.println("No type found");
                            continue;
                        }

                        System.out.println("Which item is to be edited?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        action = action.concat(readKeyboard + "_");
                        System.out.println("New value?: ");
                        readKeyboard = keyboardScanner.nextLine();

                        //Birth date needs to be checked for proper format
                        if (isBirth) {
                            if (!readKeyboard.matches("^\\s*(3[01]|[12][0-9]|0?[1-9])-(1[012]|0?[1-9])-((?:19|20)\\d{2})\\s*$")) {
                                System.out.println("Bad date format, should be d-m-yyyy");
                                continue;
                            }
                        }

                        action = action.concat(readKeyboard);
                        System.out.println("Produced action: " + action);
                        data.put("action", action);
                    } else if (readKeyboard.matches("remove")) { //PRETENDE REMOVER
                        String action = "";
                        System.out.println("Album, music or artist?: "); //Remover o quê?
                        readKeyboard = keyboardScanner.nextLine();
                        if (readKeyboard.matches("album")) {
                            action = action.concat(String.valueOf(Request.REMOVE_ALBUM) + "_");
                        } else if (readKeyboard.matches("artist")) {
                            action = action.concat(String.valueOf(Request.REMOVE_ARTIST) + "_");
                        } else if (readKeyboard.matches("music")) {
                            action = action.concat(String.valueOf(Request.REMOVE_MUSIC) + "_");
                        } else {
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
                else if (readKeyboard.matches("3")) { //
                    System.out.println("Username?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("username", readKeyboard);

                    String action = "";
                    // TODO test search feature

                    System.out.println("Search artist, music or album?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    if (readKeyboard.matches("artist")) {
                        action = action.concat(String.valueOf(Request.SEARCH_ARTIST) + "_");
                        System.out.println("Artist name?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        action = action.concat(String.valueOf(Request.SEARCH_BY_NAME) + "_" + readKeyboard);
                    }
                    // TODO (optional) mudar a forma de construir a ação (não obrigar o user a escrever name_"nome"_genre_"jazz")
                    else if (readKeyboard.matches("album")) {
                        action = action.concat(String.valueOf(Request.SEARCH_ALBUM) + "_");
                        System.out.println("Parameters to search?(at least one of these- name, artist, genre)" +
                                "(format: name_\"value\"_genre_\"jazz\", for example): ");
                        readKeyboard = keyboardScanner.nextLine();

                        //Check format
                        if (!readKeyboard.matches("([a-zA-Z]+(?:_[a-zA-Z]+)*)")) {
                            System.out.println("Bad string format");
                            continue;
                        }

                        //Decode input
                        String[] tokens = readKeyboard.split("\\_");
                        for (int i = 0; i < tokens.length; i += 2) {
                            if (tokens[i].matches("name")) {
                                action = action.concat(String.valueOf(Request.SEARCH_BY_NAME) + "_" + tokens[i + 1]);
                            } else if (tokens[i].matches("artist")) {
                                action = action.concat(String.valueOf(Request.SEARCH_BY_ARTIST) + "_" + tokens[i + 1]);
                            } else if (tokens[i].matches("genre")) {
                                action = action.concat(String.valueOf(Request.SEARCH_BY_GENRE) + "_" + tokens[i + 1]);
                            }
                        }
                    } else if (readKeyboard.matches("music")) {
                        action = action.concat(String.valueOf(Request.SEARCH_MUSIC) + "_");
                        System.out.println("Parameters to search?(at least one of these- name, artist, album)" +
                                "(format: name_\"value\"_album_\"album name\", for example): ");
                        readKeyboard = keyboardScanner.nextLine();

                        //Check format
                        // TODO arranjar esta expressao regular para so aceitar numeros em cada valor par (1_2_3_4)
                        if (!readKeyboard.matches("([a-zA-Z0-9]+(?:_[a-zA-Z0-9]+)*)")) {
                            System.out.println("Bad string format");
                            continue;
                        }

                        //Decode input
                        String[] tokens = readKeyboard.split("_");
                        for (int i = 0; i < tokens.length; i += 2) {
                            if (tokens[i].matches("name")) {
                                action = action.concat(String.valueOf(Request.SEARCH_BY_NAME) + "_" + tokens[i + 1]);
                            } else if (tokens[i].matches("artist")) {
                                action = action.concat(String.valueOf(Request.SEARCH_BY_ARTIST) + "_" + tokens[i + 1]);
                            } else if (tokens[i].matches("album")) {
                                action = action.concat(String.valueOf(Request.SEARCH_BY_ALBUM) + "_" + tokens[i + 1]);
                            }
                            if ((i + 2) < tokens.length) action = action.concat("_");
                        }
                    } else {
                        System.out.println("Bad token");
                        continue;
                    }

                    data.put("action", action);
                }
                h.receive(data);
            }
        } catch (ConnectException e) {
            try {
                remake();
            } catch (RemoteException e1) {
                e1.printStackTrace();
            }
        } catch(NotBoundException e){
            try {
                remake();
            } catch (RemoteException e1) {
                e1.printStackTrace();
            }
        } catch (Exception e) {
            System.out.println("Exception in main: " + e);
            e.printStackTrace();
        }
    }

}
