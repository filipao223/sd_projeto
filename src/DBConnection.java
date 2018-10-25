import org.omg.CORBA.TIMEOUT;

import java.io.IOException;
import java.io.Serializable;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DBConnection extends Thread {
    private static Connection connection;
    private static List<Integer> serverNumbers;
    private static String MULTICAST_ADDRESS = "226.0.0.1";
    private final int PORT = 7000;
    private static int PORT_STORAGE = 7003;
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
            serverNumbers.addAll(Arrays.asList(0,1,2,3,4,5,6,7,8,9,10));
            ExecutorService executor = Executors.newFixedThreadPool(3);

            socket = new MulticastSocket(PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);
            while(true){
                byte[] buffer = new byte[8192];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                try{
                    //Check if feature code is REQUEST_NUMBER
                    Map<String, Object> dataIn = (Map<String, Object>) serializer.deserialize(packet.getData());
                    System.out.println("Received: " + Integer.parseInt((String)dataIn.get("feature")));
                    if (Integer.parseInt((String)dataIn.get("feature")) == Request.REQUEST_NUMBER){ //Assign a number
                        System.out.println("New server");
                        executor.submit(new NumberAssigner(packet, serverNumbers, connection, serverNumbers.size()));
                    }
                    else if (Integer.parseInt((String)dataIn.get("feature")) == Request.DB_ACCESS){ //DB ACCESS
                        System.out.println("Database access requested");
                        DatabaseHandler task = new DatabaseHandler((String)dataIn.get("username"), connection, (String)dataIn.get("sql"), (boolean)dataIn.get("isquery"),
                                (String)dataIn.get("columns"),(int)dataIn.get("server"), (int)dataIn.get("feature_requested"));
                        executor.submit(task);
                    }
                    else if (Integer.parseInt((String)dataIn.get("feature")) == Request.STORAGE_ACCESS){
                        System.out.println("Storage access requested");
                        //Send ip of this machine back
                        StorageHandler task = new StorageHandler((String)dataIn.get("music"), (int)dataIn.get("serverNumber"));
                        executor.submit(task);
                    }

                } catch (Exception e){
                    e.printStackTrace();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class StorageHandler implements Runnable{
    private String fileName;
    private static String MULTICAST_ADDRESS = "226.0.0.1";
    private static int PORT_STORAGE = 7003;
    private int serverNumber;
    private static Serializer s = new Serializer();
    private static int TIMEOUT = 5000; //5 seconds

    StorageHandler(String fileName, int serverNumber){
        this.fileName = fileName;
        this.serverNumber = serverNumber;
    }

    @Override
    public void run() {
        try{
            MulticastSocket storage = new MulticastSocket(PORT_STORAGE);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            storage.joinGroup(group);

            InetAddress address = InetAddress.getLocalHost();

            Map<String, Object> dataOut = new HashMap<>();

            dataOut.put("serverNumber", serverNumber);
            dataOut.put("feature", String.valueOf(Request.STORAGE_IP));
            dataOut.put("ip", address.getHostAddress());

            byte[] buffer = s.serialize(dataOut);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT_STORAGE);

            storage.send(packet);
            storage.close();

            //Await a TCP connection from the RequestHandler
            ServerSocket server = new ServerSocket(PORT_STORAGE);
            server.setSoTimeout(TIMEOUT);
            while (true){
                server.accept();
                System.out.println("Accepted connection from RequestHandler");
                break;
            }

            server.close();

        } catch (IOException e) {
            if (e instanceof SocketTimeoutException){
                System.out.println("Connection timed out");
            }
            else e.printStackTrace();
        }
    }
}

class NumberAssigner implements Runnable{

    private DatagramPacket client;
    private List<Integer> serverNumbers;
    private Connection connection;
    private int maxNumber;
    private final static String MULTICAST_ADDRESS = "226.0.0.1";
    private final static int PORT = 7000;

    NumberAssigner(DatagramPacket client, List<Integer> serverNumbers, Connection connection, int maxNumber){
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
            data.put("feature", String.valueOf(Request.ASSIGN_NUMBER));
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

class DatabaseHandler implements Runnable{
    private Connection connection;
    private String sql;
    private boolean isQuery;
    private String columnsToGet; //columns separated by "_"
    private final static String MULTICAST_ADDRESS = "226.0.0.1";
    private final static int PORT = 7000;
    private final static int PORT_DB_ANSWER = 7001;
    private int serverNumber;
    private String user;
    private int feature;

    DatabaseHandler(String user, Connection connection, String sql, boolean isQuery, String columnsToGet, int serverNumber, int feature){
        this.user = user;
        this.connection = connection;
        this.sql = sql;
        this.isQuery = isQuery;
        this.columnsToGet = columnsToGet;
        this.serverNumber = serverNumber;
        this.feature = feature;
    }

    @Override
    public void run() {
        try{
            //Get a database connection
            connect();
            Statement statement = connection.createStatement();

            Serializer serializer = new Serializer();
            Map<String, Object> data = new HashMap<>();

            //Check if it is query or update
            if (isQuery){
                //Execute the query
                ResultSet rs = statement.executeQuery(sql);
                //Split the column names
                String[] splitColumns = columnsToGet.split("_");

                //Create string array to be sent back
                String allResults = null;

                allResults = String.valueOf(splitColumns.length)+"_";
                while(rs.next()){
                    for (String column:splitColumns){
                        allResults = allResults.concat(column+"_"+rs.getString(column)+"_");
                    }
                }
                connection.close();

                System.out.println("Allresults: " + allResults);
                data.put("results", allResults);
            }
            else{
                //Its update
                int rc = statement.executeUpdate(sql);
                data.put("results", String.valueOf(rc));
            }

            data.put("server", String.valueOf(serverNumber));
            data.put("feature", String.valueOf(Request.DB_ANSWER));
            data.put("feature_requested", String.valueOf(feature));
            data.put("username", user);

            byte[] buffer = serializer.serialize(data);

            MulticastSocket socket = new MulticastSocket();
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT_DB_ANSWER);
            socket.send(packet);

        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Small method to open a database connection
     * @throws SQLException
     */
    private void connect() throws SQLException {
        // create the connection
        connection = DriverManager.getConnection("jdbc:sqlite:database/sd.db");
    }

    /**
     * Small method to close a database connection
     * @throws SQLException
     */
    private void disconnect() throws SQLException {
        // create the connection
        connection.close();
    }
}