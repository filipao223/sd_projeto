import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Thread.sleep;

public class MulticastServer extends Thread {

    private String MULTICAST_ADDRESS = "224.3.2.1";
    private int PORT = 4321;
    //private long SLEEP_TIME = 5000;
    private byte[] bufferReceive;

    public static void main(String[] args) {
        MulticastServer server = new MulticastServer();
        server.start();
    }

    public MulticastServer() {
        super("Server " + (long) (Math.random() * 1000));
    }

    @SuppressWarnings("unchecked")
    public void run() {

        Task task;
        ExecutorService executor = Executors.newSingleThreadExecutor();

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


                Map<String, Object> dataRec;
                try{
                    dataRec = (Map<String, Object>) s.deserialize(packetIn.getData());

                    //Check which feature user wants to do
                    int code = Integer.parseInt((String)dataRec.get("feature"));
                    switch(code){
                        case Request.LOGIN:
                            //Get username
                            String user = (String) dataRec.get("username");
                            String message = "User \"" + user + "\" wants to login";
                            task = new Task(message);
                            executor.submit(task);
                            break;
                        case Request.DOWNLOAD:
                            //Get username
                            user = (String) dataRec.get("username");
                            message = "User \"" + user + "\" wants to download";
                            task = new Task(message);
                            executor.submit(task);
                    }

                } catch (ClassNotFoundException | ClassCastException e){
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
