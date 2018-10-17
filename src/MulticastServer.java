import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Thread.sleep;

public class MulticastServer extends Thread {

    private String MULTICAST_ADDRESS = "224.3.2.1";
    private int PORT = 4321;
    private byte[] bufferReceive;
    private Connection mainDatabaseConnection;
    private int serverNumber;

    public static void main(String[] args) {
        //MulticastServer server = new MulticastServer();
        //server.start();
    }

    public MulticastServer(Connection mainDatabaseConnection, int serverNumber) {
        super("Server " + (long) (Math.random() * 1000));
        this.mainDatabaseConnection = mainDatabaseConnection;
        this.serverNumber = serverNumber;
    }

    @SuppressWarnings("unchecked")
    public void run() {

        System.out.println(System.getProperty("user.dir"));

        RequestHandler handler;
        ExecutorService executor = Executors.newFixedThreadPool(10);

        Serializer s = new Serializer();
        MulticastSocket socket = null;
        //long counter = 0;
        System.out.println(this.getName() + " running...");
        try {
            socket = new MulticastSocket(PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);

            while (true) {
                bufferReceive = new byte[256];
                DatagramPacket packetIn = new DatagramPacket(bufferReceive, bufferReceive.length);
                socket.receive(packetIn);

                //Map<String, Object> dataRec;
                try{
                    //dataRec = (Map<String, Object>) s.deserialize(packetIn.getData());

                    handler = new RequestHandler(packetIn, mainDatabaseConnection, serverNumber);
                    executor.submit(handler);

                } catch (ClassCastException e){
                    System.out.println("Error casting deserialized packet data: " + e);
                } catch (NumberFormatException e){
                    System.out.println("Error parsing integer: " + e);
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            socket.close();
        }
    }
}

/*class MulticastSender extends Thread{
    private String MULTICAST_ADDRESS = "224.0.224.0";
    private int PORT = 4321;

    public MulticastSender() {
        super("Sender " + (long) (Math.random() * 1000));
    }

    public void run() {
        MulticastSocket socket = null;
        System.out.println(this.getName() + " ready...");

        try{
            socket = new MulticastSocket(); //Sending
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);



        } catch (IOException e){
            System.out.println("Exception: " + e);
        }

    }
}*/
