import java.net.MulticastSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.IOException;
import java.util.*;

/**
 * The MulticastClient class joins a multicast group and loops receiving
 * messages from that group. The client also runs a MulticastUser thread that
 * loops reading a string from the keyboard and multicasting it to the group.
 * <p>
 * The example IPv4 address chosen may require you to use a VM option to
 * prefer IPv4 (if your operating system uses IPv6 sockets by default).
 * <p>
 * Usage: java -Djava.net.preferIPv4Stack=true MulticastClient
 *
 * @author Raul Barbosa
 * @version 1.0
 */
public class MulticastClient extends Thread {
    private String MULTICAST_ADDRESS = "224.3.2.1";
    private int PORT = 4321;
    private Serializer s = new Serializer();
    private static List<Integer> serverNumbers = new ArrayList<>();

    public static void main(String[] args) {
        MulticastClient client = new MulticastClient();
        client.start();
        MulticastUser user = new MulticastUser(serverNumbers);
        user.start();
    }

    @SuppressWarnings("unchecked")
    public void run() {
        MulticastSocket socket = null;
        try {
            socket = new MulticastSocket(PORT);  // create socket and bind it
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);
            while (true) {
                byte[] buffer = new byte[256];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                Map<String, Object> data = (Map<String, Object>) s.deserialize(buffer);
                if (((String)data.get("feature")).matches("13")){
                    System.out.println("------------Callback is: ");
                    System.out.println("Feature: " + data.get("feature"));
                    System.out.println("Username: " + data.get("username"));
                    System.out.println("Resposta: " + data.get("answer"));
                    System.out.println("Opcional: " + data.get("null"));
                    System.out.println("-----------Done");
                }
                else if (((String)data.get("feature")).matches("7")){
                    System.out.println("-----------New note: ");
                    System.out.println(data.get("username") + " was made editor");
                }
                else if (((String)data.get("feature")).matches("9")){
                    System.out.println("-----------New notes for " + data.get("username") + ": ");
                    String notes = (String) data.get("notes");
                    for (String note:notes.split("\\|")){
                        System.out.println(note);
                    }
                    System.out.println("-----------Done");
                }
                else if(((String)data.get("feature")).matches("30")){
                    System.out.println("-----------New server (" + data.get("new_server") + ")------------");
                    serverNumbers.add((int)data.get("new_server"));
                }
                else if(((String)data.get("feature")).matches("31")){
                    System.out.println("-----------Server down (" + data.get("server_down") + ")------------");
                    if (!serverNumbers.isEmpty()) serverNumbers.remove((int)data.get("server_down"));
                }
            }
        } catch (IOException | ClassNotFoundException e) {
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
        // TODO on startup, client should check if a multicast server is available
        Serializer s = new Serializer();
        MulticastSocket socket = null;
        System.out.println(this.getName() + " ready...");
        try {
            socket = new MulticastSocket();  // create socket without binding it (only for sending)
            Scanner keyboardScanner = new Scanner(System.in);
            while (true) {
                boolean alreadyGotFeatureCode = false;
                Map<String, Object> data = new HashMap<>();
                //Get random number
                Random r = new Random();
                String readKeyboard = "";
                if (serverNumbers.isEmpty()){
                    System.out.println("Feature?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    //If still no servers, continue
                    if (serverNumbers.isEmpty()){
                        System.out.println("No servers available");
                        continue;
                    }
                    //Servers available
                    alreadyGotFeatureCode = true;
                }
                int index = r.nextInt(serverNumbers.size());
                data.put("server", String.valueOf(serverNumbers.get(index)));
                System.out.println("==========Server " + serverNumbers.get(index) + "============");
                if (!alreadyGotFeatureCode){
                    System.out.println("Feature?: ");
                    readKeyboard = keyboardScanner.nextLine();
                }
                data.put("feature", readKeyboard);

                if (readKeyboard.matches("1") || readKeyboard.matches("29")){
                    System.out.println("Username?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("username", readKeyboard);

                    System.out.println("Password?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("password", readKeyboard);
                }
                else if(readKeyboard.matches("6")){
                    System.out.println("Username?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("editor", readKeyboard);

                    System.out.println("New editor?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("newEditor", readKeyboard);
                }
                else if(readKeyboard.matches("14")){
                    System.out.println("Username?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("username", readKeyboard);
                }
                else if(readKeyboard.matches("2")){
                    System.out.println("Username?: ");
                    readKeyboard = keyboardScanner.nextLine();
                    data.put("username", readKeyboard);
                    System.out.println("Add, remove or edit?: ");
                    readKeyboard = keyboardScanner.nextLine();

                    if (readKeyboard.matches("add")){
                        String action = "";
                        System.out.println("Album or artist?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        if (readKeyboard.matches("album")){
                            action = action.concat(String.valueOf(Request.ADD_ALBUM)+"_");
                        }
                        else if (readKeyboard.matches("artist")){
                            action = action.concat(String.valueOf(Request.ADD_ARTIST)+"_");
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

                    else if (readKeyboard.matches("edit")){
                        System.out.println("Which data type to edit?(music, album or artist): ");
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
                        if (isBirth){
                            if (!readKeyboard.matches("^\\s*(3[01]|[12][0-9]|0?[1-9])-(1[012]|0?[1-9])-((?:19|20)\\d{2})\\s*$")){
                                System.out.println("Bad date format, should be d-m-yyyy");
                                continue;
                            }
                        }

                        action = action.concat(readKeyboard);
                        System.out.println("Produced action: " + action);
                        data.put("action", action);
                    }
                    else if (readKeyboard.matches("remove")){
                        String action = "";
                        System.out.println("Album or artist?: ");
                        readKeyboard = keyboardScanner.nextLine();
                        if (readKeyboard.matches("album")){
                            action = action.concat(String.valueOf(Request.REMOVE_ALBUM)+"_");
                        }
                        else if (readKeyboard.matches("artist")){
                            action = action.concat(String.valueOf(Request.REMOVE_ARTIST)+"_");
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

                }

                byte[] buffer = s.serialize(data);

                InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                socket.send(packet);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            socket.close();
        }
    }
}
