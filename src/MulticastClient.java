import java.net.MulticastSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

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

    public static void main(String[] args) {
        MulticastClient client = new MulticastClient();
        client.start();
        MulticastUser user = new MulticastUser();
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

    public MulticastUser() {
        super("User " + (long) (Math.random() * 1000));
    }

    public void run() {
        Serializer s = new Serializer();
        MulticastSocket socket = null;
        System.out.println(this.getName() + " ready...");
        try {
            socket = new MulticastSocket();  // create socket without binding it (only for sending)
            Scanner keyboardScanner = new Scanner(System.in);
            while (true) {
                Map<String, Object> data = new HashMap<>();
                System.out.println("Feature?: ");
                String readKeyboard = keyboardScanner.nextLine();
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
