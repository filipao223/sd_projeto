import java.io.*;
import java.net.*;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Thread.sleep;

/**
 * Class that accepts incoming requests and creates task Runnables to process them,
 * using a fixed size thread pool
 */
public class MulticastServer extends Thread {

    private static String MULTICAST_ADDRESS = "224.3.2.1";
    private static int PORT = 4321;
    private static int PORT_DBCONNECTION = 7000;
    private static byte[] bufferReceive;
    private Connection mainDatabaseConnection;
    private int serverNumber;

    @SuppressWarnings("unchecked")
    /**
     * After running a MulticastServer, a new connection to DBConnection is opened,
     * requesting a common database connection object, to handle synchronization issues,
     * and a server number, which will be used as an indicator of which packet each server
     * processes.
     * <p>
     * Following this procedure, a UDP datagram is sent to the RMI Servers notifying them this
     * server is ready to process requests.
     */
    public static void main(String[] args) {
        //Request server number and database connection
        try {
            Map<String, Object> dataIn = null;
            while(true){
                System.out.println("Requesting server number and database connection");
                //Send request
                MulticastSocket serverConnection = new MulticastSocket();
                InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                serverConnection.joinGroup(group);
                bufferReceive = new byte[256];
                Map<String, Object> data = new HashMap<>(); Serializer serializer = new Serializer();
                data.put("feature", Request.REQUEST_NUMBER);
                bufferReceive = serializer.serialize(data);
                DatagramPacket out = new DatagramPacket(bufferReceive, bufferReceive.length, group, PORT_DBCONNECTION);
                serverConnection.send(out);

                //Now wait for the response
                serverConnection = new MulticastSocket(PORT_DBCONNECTION);
                serverConnection.joinGroup(group);
                serverConnection.setSoTimeout(5000);
                bufferReceive = new byte[2048];
                try{
                    DatagramPacket packet = new DatagramPacket(bufferReceive, bufferReceive.length);
                    serverConnection.receive(packet);
                    Serializer s = new Serializer();
                    dataIn = (Map<String, Object>) s.deserialize(bufferReceive);
                    break;
                } catch (ClassNotFoundException | SocketTimeoutException e){
                    if (e instanceof ClassNotFoundException){
                        System.out.println("Error getting server number and connection from DBConnection, retrying");
                    }
                    else{
                        System.out.println("No response in allocated time, retrying");
                    }
                }
            }

            //Start the server
            MulticastServer server = new MulticastServer((Connection)dataIn.get("connection"), (int)dataIn.get("serverNumber"));
            server.start();
            notify((int)dataIn.get("serverNumber"));
            //Notify RMI Server that it is available to receive requests
        } catch (IOException e) {
            e.printStackTrace();
            if(e instanceof SocketTimeoutException){
                System.out.println("No response in allocated time");
                e.printStackTrace();
            }
            else{
                e.printStackTrace();
            }
        }
    }

    /**
     * Sends an UDP datagram to the RMI Servers with a given server number, notifying them
     * of this server's availability
     * @param serverNumber this server's number
     * @throws IOException
     */
    public static void notify(int serverNumber) throws IOException {
        //Create multicast socket
        MulticastSocket socket = new MulticastSocket();
        Serializer s = new Serializer();

        //Create data map
        Map<String, Object> callback = new HashMap<>();
        callback.put("feature", String.valueOf(Request.NEW_SERVER));
        callback.put("new_server", serverNumber);

        byte[] buffer = s.serialize(callback);

        //Create udp packet
        InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
        socket.send(packet);
    }

    /**
     * MulticastServer constructor
     * @param mainDatabaseConnection shared database connection object
     * @param serverNumber this server's assigned number
     */
    public MulticastServer(Connection mainDatabaseConnection, int serverNumber) {
        super("Server " + serverNumber);
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
                try{
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
