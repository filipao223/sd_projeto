import com.sun.corba.se.spi.orbutil.threadpool.Work;
import com.sun.org.apache.xpath.internal.operations.Mult;

import javax.xml.crypto.Data;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DBConnection extends Thread {
    private static Connection connection;
    private static List<Integer> serverNumbers;
    private static String MULTICAST_ADDRESS = "224.3.2.1";
    private final int PORT = 7000;
    private Serializer serializer = new Serializer();

    public static void main(String args[]){
        DBConnection dbConnection = new DBConnection();
        dbConnection.start();
    }

    @SuppressWarnings("unchecked")
    public void run(){
        try {
            System.out.println("Listening for requests");
            MulticastSocket socket = null;

            serverNumbers = new ArrayList<>();
            serverNumbers.add(1); serverNumbers.add(2); serverNumbers.add(0);
            ExecutorService executor = Executors.newFixedThreadPool(3);

            socket = new MulticastSocket(PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);
            while(true){
                byte[] buffer = new byte[256];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                //Check if feature code is REQUEST_NUMBER
                Map<String, Object> dataIn = (Map<String, Object>) serializer.deserialize(packet.getData());
                if ((int)dataIn.get("feature") != Request.REQUEST_NUMBER) continue;

                //Socket client = socket.accept();
                System.out.println("New server");
                executor.submit(new Worker(packet, serverNumbers, connection, serverNumbers.size()));
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}

class Worker implements Runnable{

    private DatagramPacket client;
    private List<Integer> serverNumbers;
    private Connection connection;
    private int maxNumber;
    private final static String MULTICAST_ADDRESS = "224.3.2.1";
    private final static int PORT = 7000;

    Worker(DatagramPacket client, List<Integer> serverNumbers, Connection connection, int maxNumber){
        this.client = client;
        this.serverNumbers = serverNumbers;
        this.connection = connection;
        this.maxNumber = maxNumber;
    }

    public void run(){
        try{
            if (serverNumbers.isEmpty()){
                System.out.println("No more server numbers");
                return;
            }
            //Select random number from list
            Random r = new Random();
            int index = r.nextInt(maxNumber);

            Serializer serializer = new Serializer();
            Map<String, Object> data = new HashMap<>();
            data.put("feature", Request.ASSIGN_NUMBER);
            data.put("connection", connection);
            data.put("serverNumber", serverNumbers.get(index));

            serverNumbers.remove(index);

            byte[] buffer = serializer.serialize(data);

            MulticastSocket socket = new MulticastSocket();
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
            socket.send(packet);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}