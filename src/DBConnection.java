import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Class responsible for receiving UDP packets with database and disk storage access requests, and server number
 * assignments.
 * <p>
 * For a database access, an instance of {@link DatabaseHandler} is created.<br>
 * For a storage access, an instance of {@link UploadHandler} is created.<br>
 * For a new server number assignment, an instance of {@link NumberAssigner} is created.
 * @author Joao Montenegro
 */
public class DBConnection extends Thread {
    private static Connection connection;
    private static List<Integer> serverNumbers;
    private static String MULTICAST_ADDRESS = "226.0.0.1";
    private final int PORT = 7000;
    private static int PORT_STORAGE = 7003;
    private Serializer serializer = new Serializer();

    /**
     * Starts the server.
     * @param args
     * @author Joao Montenegro
     */
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
            ExecutorService executor = Executors.newFixedThreadPool(10);

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
                    else if (Integer.parseInt((String)dataIn.get("feature")) == Request.UP_TCP){
                        System.out.println("Upload access requested");

                        UploadHandler task = new UploadHandler((String)dataIn.get("music"), (int)dataIn.get("serverNumber"), (String)dataIn.get("clientIp"),
                                (String)dataIn.get("username"));
                        executor.submit(task);
                    }
                    else if (Integer.parseInt((String)dataIn.get("feature")) == Request.DOWNLOAD){
                        System.out.println("Download access requested");
                        DownloadHandler task = new DownloadHandler((String)dataIn.get("music"), (int)dataIn.get("serverNumber"), (String)dataIn.get("clientIp"),
                                (String)dataIn.get("username"));
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

class DownloadHandler implements Runnable{
    private String fileName;
    private static String MULTICAST_ADDRESS = "226.0.0.1";
    private static int PORT = 7003;
    private static int PORT_CLIENT = 4321;
    private int serverNumber;
    private String clientIp;
    private String username;
    private static Serializer s = new Serializer();
    private static int TIMEOUT = 5000; //5 seconds

    DownloadHandler(String fileName, int serverNumber, String clientIp, String username) {
        this.fileName = fileName;
        this.serverNumber = serverNumber;
        this.clientIp = clientIp;
        this.username = username;
    }

    @Override
    public void run() {
        try{
            MulticastSocket storage = new MulticastSocket(PORT_CLIENT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            storage.joinGroup(group);

            InetAddress address = InetAddress.getLocalHost();

            Map<String, Object> dataOut = new HashMap<>();

            dataOut.put("username", username);
            dataOut.put("serverNumber", serverNumber);
            dataOut.put("feature", String.valueOf(Request.DOWN_TCP));
            dataOut.put("address", address.getHostAddress());
            dataOut.put("musicName", fileName);

            byte[] buffer = s.serialize(dataOut);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT_CLIENT);

            storage.send(packet);

            //Await a TCP connection from the client
            ServerSocket server = new ServerSocket(PORT);
            server.setSoTimeout(TIMEOUT);
            while (true){
                Socket client = server.accept();
                System.out.println("Accepted connection from client");

                //Write bytes
                if (client.getLocalAddress().getHostAddress().matches(clientIp)) { //If it's the correct client connecting
                    //Receive data
                    //Convert music file to byte array
                    File file = new File("music/" + fileName + ".txt");
                    ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                    byte[] fileContent = Files.readAllBytes(file.toPath());
                    MusicFile music = new MusicFile(fileContent);

                    out.writeObject(music);

                    out.close();
                    System.out.println("Uploaded to client");

                    //Close connection
                    client.close();
                    server.close();

                    //Report success to multicast server
                    dataOut = new HashMap<>();
                    dataOut.put("server", serverNumber);
                    dataOut.put("response", "Success");

                    buffer = s.serialize(dataOut);
                    packet = new DatagramPacket(buffer, buffer.length, group, PORT);

                    storage.send(packet);

                    break;
                }
                System.out.println("No match");
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

/**
 * Handles all disk storage access.
 * <p>
 * When this class is instantiated, it receives the server number of the requesting multicast server, the file name
 * of the music which is to be uploaded and the machine's IP address from which the upload is to be done from (the client's).
 * <p>
 * First step is to send an UDP packet directly to the RMI Server containing this machine's IP, in order to start the upload over TCP.
 * <p>
 * After this, using a ServerSocket, the method waits for a user to connect to this socket, using a timeout to prevent failed connections
 * from blocking this thread.
 * <p>
 * Following a successful connection, the music file is uploaded and written to disk, and a reply to the multicast server is sent, reporting
 * the success of the operation.
 * @author Joao Montenegro
 */
class UploadHandler implements Runnable{
    private String fileName;
    private static String MULTICAST_ADDRESS = "226.0.0.1";
    private static int PORT = 7003;
    private static int PORT_CLIENT = 4321;
    private int serverNumber;
    private String clientIp;
    private String username;
    private static Serializer s = new Serializer();
    private static int TIMEOUT = 5000; //5 seconds

    /**
     * Constructor of UploadHandler.
     * @param fileName Filename of the music which is to be uploaded.
     * @param serverNumber Number of the server that requested the operation.
     * @param clientIp IP address of the client that wants to upload the file.
     * @author Joao Montenegro
     */
    UploadHandler(String fileName, int serverNumber, String clientIp, String username){
        this.fileName = fileName;
        this.serverNumber = serverNumber;
        this.clientIp = clientIp;
        this.username = username;
    }

    @Override
    public void run() {
        try{
            MulticastSocket storage = new MulticastSocket(PORT_CLIENT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            storage.joinGroup(group);

            InetAddress address = InetAddress.getLocalHost();

            Map<String, Object> dataOut = new HashMap<>();

            dataOut.put("username", username);
            dataOut.put("serverNumber", serverNumber);
            dataOut.put("feature", String.valueOf(Request.UP_TCP));
            dataOut.put("address", address.getHostAddress());
            dataOut.put("musicName", fileName);

            byte[] buffer = s.serialize(dataOut);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT_CLIENT);

            storage.send(packet);

            //Await a TCP connection from the client
            ServerSocket server = new ServerSocket(PORT);
            server.setSoTimeout(TIMEOUT);
            while (true){
                Socket client = server.accept();
                System.out.println("Accepted connection from RequestHandler");

                //Receive bytes
                if (client.getLocalAddress().getHostAddress().matches(clientIp)) { //If it's the correct client connecting
                    //Receive data
                    ObjectInputStream inFromClient =
                            new ObjectInputStream(client.getInputStream());
                    Object file = null;
                    try {
                        file = inFromClient.readObject();
                    } catch (EOFException e) {
                        System.out.println("Finished reading object");
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }

                    System.out.println("User uploaded: " + file);

                    //Close connection
                    client.close();
                    server.close();

                    //Convert byte array back to a file
                    MusicFile musicFile = (MusicFile) file;

                    //Write the file to disk
                    FileOutputStream outFile = new FileOutputStream("music/" + fileName + ".txt");
                    if (musicFile == null){
                        System.out.println("Music file is null");
                    }
                    else{
                        outFile.write(musicFile.fileContent);
                    }

                    outFile.close();

                    //Report success to multicast server
                    dataOut = new HashMap<>();
                    dataOut.put("server", serverNumber);
                    dataOut.put("response", "Success");

                    buffer = s.serialize(dataOut);
                    packet = new DatagramPacket(buffer, buffer.length, group, PORT);

                    storage.send(packet);

                    break;
                }
                System.out.println("No match");
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

/**
 * Handles server number assignment.
 * <p>
 * First, it checks if there are available numbers to assign. The numbers are in an integer List.
 * If there are, then a random index is generated, and a number is removed from the list and sent back to the server
 * in an UDP packet
 * @author Joao Montenegro
 */
class NumberAssigner implements Runnable{

    private DatagramPacket client;
    private List<Integer> serverNumbers;
    private Connection connection;
    private int maxNumber;
    private final static String MULTICAST_ADDRESS = "226.0.0.1";
    private final static int PORT = 7000;

    /**
     * Constructor of NumberAssigner
     * @param client The received UDP datagram.
     * @param serverNumbers The integer List containing available numbers to assign.
     * @param connection
     * @param maxNumber Max index of the list, used in Random object
     */
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

            //Send it back to the server
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

// TODO complete javadoc

/**
 * Handles all database interaction.
 * <p>
 * First step is to check if the SQL command is a query (of the SELECT type) or a table update, which return different data types.<br>
 * After this, the query is executed, and the string containing which columns for each row should be sent back to the multicast server is decoded, being
 * in the format "column1_column2_..._columnN_". The results are then extracted from the ResultSet.<br>
 * If it was not a query, a simple integer is placed in the string.
 * <p>
 * The string is sent back to the multicast server via UDP and multicast.
 * @author Joao Montenegro
 */
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

    /**
     * Constructor of DatabaseHandler
     * @param user The user that requested the database access.
     * @param connection The shared database connection that is to be used.
     * @param sql The SQL query to be executed.
     * @param isQuery If the query is of the 'SELECT' type.
     * @param columnsToGet What columns the query is to get for each row.
     * @param serverNumber The multicast server that requested the access.
     * @param feature The original feature that resulted in a database access.
     * @author Joao Montenegro
     */
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
     * Opens a database connection
     * @throws SQLException
     * @author Joao Montenegro
     */
    private void connect() throws SQLException {
        // create the connection
        connection = DriverManager.getConnection("jdbc:sqlite:database/sd.db");
    }
}