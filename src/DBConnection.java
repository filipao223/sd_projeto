import com.sun.corba.se.spi.orbutil.threadpool.Work;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DBConnection extends Thread {
    private static Connection connection;
    private static List<Integer> serverNumbers;
    private final int PORT = 7000;

    public static void main(String args[]){
        DBConnection dbConnection = new DBConnection();
        dbConnection.start();
    }

    public void run(){
        try {
            ServerSocket socket = new ServerSocket(PORT);
            serverNumbers = new ArrayList<>();
            serverNumbers.add(1); serverNumbers.add(2); serverNumbers.add(0); serverNumbers.add(3);
            ExecutorService executor = Executors.newFixedThreadPool(3);
            while(true){
                Socket client = socket.accept();
                System.out.println("Accepted client");
                executor.submit(new Worker(client, serverNumbers, connection, serverNumbers.size()));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class Worker implements Runnable{

    private Socket client;
    private List<Integer> serverNumbers;
    private Connection connection;
    private int maxNumber;

    Worker(Socket client, List<Integer> serverNumbers, Connection connection, int maxNumber){
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
            data.put("connection", connection);
            data.put("serverNumber", serverNumbers.get(index));

            serverNumbers.remove(index);

            byte[] buffer = serializer.serialize(data);
            DataOutputStream out = new DataOutputStream(client.getOutputStream());
            out.write(buffer);
            client.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}