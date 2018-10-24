

import com.sun.org.apache.regexp.internal.RE;
import com.sun.org.apache.xpath.internal.operations.Mult;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;

/**
 * Runnable that handles a single UDP datagram
 */
public class RequestHandler implements Runnable {
    private static String MULTICAST_ADDRESS = "226.0.0.1";
    private static int PORT = 4321;
    private static int PORT_DBCONNECTION = 7000;
    private static int PORT_DB_ANSWER = 7001;
    private static int PORT_TCP = 7002;

    private static int TCP_LISTEN_TIMEOUT = 5000; //5 seconds

    private DatagramPacket clientPacket;
    private Map<String, Object> data = null;

    private static Serializer s = new Serializer();

    private static Connection connection;
    private static int serverNumber;

    private static String new_editor_note = "You have been made editor by user ";
    private static String new_edit = "New edit: ";

    private static int NO_LOGIN        = 1;
    private static int NO_USER_FOUND   = 2;
    private static int ALREADY_LOGIN   = 3;
    private static int ALREADY_EDITOR  = 4;
    private static int NOT_EDITOR      = 5;
    private static int DB_EXCEPTION    = 6;
    private static int TIMEOUT         = 7;

    private static String DB_FIELD_NAME        = "name"       ;
    private static String DB_FIELD_YEAR        = "year"       ;
    private static String DB_FIELD_ALBUM       = "album"      ;
    private static String DB_FIELD_ARTIST      = "artist"     ;
    private static String DB_FIELD_BIRTH       = "birth"      ;
    private static String DB_FIELD_DESCRIPTION = "description";
    private static String DB_FIELD_GENRE       = "genre"      ;
    private static String DB_FIELD_LYRICS      = "lyrics"     ;

    // TODO Update javadocs


    /**
     * Constructor for request handler, uses the UDP datagram received by the server and a database
     * connection to serve the request
     * @param packet UDP datagram received
     * @param connection Database connection passed by the server, this connection object is shared
     *                   between all request handlers, in order to solve synchronization issues
     */
    RequestHandler(DatagramPacket packet, Connection connection, int serverNumber){
        this.clientPacket = packet;
        RequestHandler.connection = connection;
        RequestHandler.serverNumber = serverNumber;
    }

    @SuppressWarnings("unchecked")

    /**
     * Main thread code.
     *<p>
     * Before starting to process the client's request, the server number of the packet is checked against
     * the server's number. If it matches, processing continues, if it doesn't, the thread simply returns,
     * another server will handle processing of this request.
     *<p>
     * Next step is to check which feature was requested. The feature number is extracted from the packet,
     * and, using a simple switch, is checked against Request codes.
     */
    public void run(){
        try{
            data = (Map<String, Object>) s.deserialize(clientPacket.getData());
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        try{
            if (data != null){
                //First check if request is about checking if server is up
                if (Integer.parseInt((String)data.get("feature")) == Request.CHECK_SERVER_UP){
                    //It is, send server number in a datagram
                    try{
                        //Internal use
                        //Send back server number
                        Map<String, Object> callback = new HashMap<>();
                        callback.put("feature", String.valueOf(Request.NEW_SERVER));
                        callback.put("new_server", serverNumber);
                        //Create multicast socket
                        MulticastSocket socket = new MulticastSocket();

                        byte[] buffer = s.serialize(callback);

                        //Create udp packet
                        InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                        socket.send(packet);
                        return;
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                //Check if server should be handling this packet
                int server = Integer.parseInt((String)data.get("server"));
                if(server != serverNumber){
                    System.out.println("Server " + serverNumber + " aborted packet processing");
                    return;
                }
                //Check which feature user wants to do
                int code = Integer.parseInt((String)data.get("feature"));
                // TODO Escrever crítica sobre um álbum (com pontuação)
                // TODO Notificação imediata de re-edição de descrição de álbum (online users)
                // TODO Partilhar um ficheiro musical e permitir o respetivo download
                switch(code){
            //-------------------------------------------------------------------------------
                    case Request.REGISTER:
                        try{
                            String user = (String) data.get("username");
                            String pass = (String) data.get("password");

                            int rc = registerHandler(user, pass);

                            if (rc==NO_USER_FOUND || rc==-1) sendCallback(user, "User already exists", null);
                            else sendCallback(user, "User registered", null);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
            //----------------------------------------------------------------------------------
                    case Request.LOGIN:
                    case Request.LOGOUT:
                        // FIXME When logging in, server returns notes, even if it is null
                        try{
                            System.out.println("User wants to login/logout");
                            String user = (String)data.get("username");
                            String password = (String) data.get("password");

                            //Check in db
                            int rc = loginHandler(user, password, code);

                            if (code == Request.LOGIN){
                                if (rc==ALREADY_LOGIN) sendCallback(user, "User already logged in", null);
                                else if (rc==NO_USER_FOUND) sendCallback(user, "User not found", null);
                                else if (rc==-1) sendCallback(user, "Wrong user/password", null);
                                else{
                                    sendCallback(user, "User logged in", null);
                                    System.out.println("Checking if notes");
                                    //Check if there are notifications
                                    String notes = getAllNotes(user);
                                    if(notes != null){
                                        System.out.println("Sending notes");
                                        //There are notes, send them
                                        sendMultipleNotifications(user, notes);
                                        System.out.println("Notes sent");
                                    }
                                    else System.out.println("No notes to send");
                                }
                            }
                            else{
                                if (rc==NO_USER_FOUND) sendCallback(user, "User not found", null);
                                else sendCallback(user, "User logged out", null);
                            }

                        } catch ( Exception e) {
                            e.printStackTrace();
                        }
                        break;
            //----------------------------------------------------------------------------------
                    case Request.MAKE_EDITOR:
                        String editor = null;
                        String newEditor = null;
                        try{
                            editor = (String) data.get("editor");
                            newEditor = (String) data.get("newEditor");

                            int rc = checkLoginState(editor);
                            System.out.println("Passed loginstate");
                            if(rc==NO_USER_FOUND) sendCallback(editor, "User not found", null);
                            else if(rc==NO_LOGIN) sendCallback(editor, "User is not logged in", null);
                            else if(rc==TIMEOUT) sendCallback(editor, "Session login timed out", null);
                            else{
                                //Check if is editor
                                rc = checkIfEditor(editor);
                                if (rc==NOT_EDITOR) sendCallback(editor, "User is not editor", null);
                                else{
                                    //Make editor
                                    rc = makeEditorHandler(editor, newEditor);
                                    if (rc==NO_USER_FOUND) sendCallback(editor, "New editor not found", null);
                                    else if(rc==-1) sendCallback(editor, "Error making new editor", null);
                                    else{
                                        sendCallback(editor, "Made new editor", null);
                                        sendSingleNotification(newEditor, editor, null, Request.NOTE_EDITOR);
                                    }
                                }
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
            //----------------------------------------------------------------------------------
                    case Request.MANAGE:
                        // FIXME Wrong return code when removing successfully
                        try{
                            String user = (String) data.get("username");
                            String action = (String) data.get("action");

                            int rc = checkLoginState(user);
                            if(rc==NO_USER_FOUND) sendCallback(user, "User not found", null);
                            else if(rc==NO_LOGIN) sendCallback(user, "User is not logged in", null);
                            else if(rc==TIMEOUT) sendCallback(user, "Session login timed out", null);
                            else{
                                //Check if is editor
                                rc = checkIfEditor(user);
                                if (rc==NOT_EDITOR) sendCallback(user, "User is not editor", null);
                                else if (rc==DB_EXCEPTION) sendCallback(user, "Database error", null);
                                else{
                                    //Make the edit
                                    rc = managerHandler(user, action);
                                    if (rc==-1) sendCallback(user, "Field not edited", null);
                                    else if (rc==-2) sendCallback(user, "Item not added", null);
                                    else if (rc==-3) sendCallback(user, "Item not removed", null);
                                    else if (rc==1) sendCallback(user, "Item added", null);
                                    else if (rc==2) sendCallback(user, "Item removed", null);
                                    else sendCallback(user, "Field edited", null);
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
            //----------------------------------------------------------------------------------
                    case Request.SEARCH:
                        try{
                            String user = (String) data.get("username");
                            String action = (String) data.get("action");

                            int rc = checkLoginState(user);
                            if(rc==NO_USER_FOUND) sendCallback(user, "User not found", null);
                            else if (rc==DB_EXCEPTION) sendCallback(user, "Database error", null);
                            else if(rc==NO_LOGIN) sendCallback(user, "User is not logged in", null);
                            else if(rc==TIMEOUT) sendCallback(user, "Session login timed out", null);
                            else{
                                //Search
                                String results = searchHandler(user, action);
                                if (results == null) sendCallback(user, "No results found", null);
                                else sendCallback(user, "Found results", results);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
            //-----------------------------------------------------------------------------------
                    case Request.DETAILS_ALBUM:
                    case Request.DETAILS_ARTIST:
                        // FIXME (maybe) should this simple query require auth?
                        // TODO use LIKE keyword in SQL query
                        try{
                            String user = (String) data.get("username");
                            String item = (code== Request.DETAILS_ALBUM ?
                                    (String)data.get("album"):(String)data.get("artist"));

                            String sql;
                            String columns;

                            //Build sql query
                            if (code == Request.DETAILS_ALBUM){
                                sql = "SELECT name, year, artist, genre FROM Albums WHERE name=\"" + item +
                                        "\" UNION SELECT name, null as col1, null as col2, null as col3 FROM Music WHERE album=\"" +
                                        item + "\";";
                                columns = "name_year_artist_genre_name";
                            }
                            else{
                                sql = "SELECT name, birth, description, null as col3 FROM Artists WHERE name=\"" + item +
                                        "\" UNION SELECT name, null as col1, null as col2, null as col3 FROM Albums WHERE artist=\"" +
                                        item + "\";";
                                columns = "name_birth_description_name";
                            }
                            //Access database
                            databaseAccess(user, sql, true, columns, code);
                            //Wait for reply
                            String results = (String) databaseReply(user, code);
                            if (results == null) sendCallback(user, "No results", null);
                            else sendCallback(user, "Results found", results);
                        } catch (Exception e){
                            e.printStackTrace();
                        }
            //---------------------------------------------------------------------------------------------
                    case Request.UPLOAD:
                        //User wants to upload a file
                        String user = (String) data.get("username");
                        String musicName = (String) data.get("music");
                        String clientIp = (String) data.get("client");

                        //First check if user is logged in
                        int rc = checkLoginState(user);

                        if (rc==NO_USER_FOUND) sendCallback(user, "User not found", null);
                        else if (rc==NO_LOGIN) sendCallback(user, "User not logged in", null);
                        else{
                            //Now check if this music exists in database
                            // TODO change request code
                            // TODO check if music already exists in the server
                            String sql = "SELECT name, uri FROM Music WHERE name=\"" + musicName + "\";";
                            databaseAccess(user, sql, true, "name", Request.UPLOAD_MUSIC);
                            String results = (String) databaseReply(user, Request.UPLOAD_MUSIC);

                            MulticastSocket socket = new MulticastSocket(PORT);

                            if (results != null){
                                //Split string, format: 1_name_music4
                                String[] tokens = results.split("_");
                                if (tokens.length < 2){
                                    System.out.println("Music not in database yet, connection refused");
                                    //Send null ip to user, not allowed
                                    data.put("feature", String.valueOf(Request.OPEN_TCP));
                                    data.put("username", user);
                                    data.put("address", null);
                                    data.put("musicName", musicName);

                                    byte[] buffer = s.serialize(data);

                                    InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                                    socket.joinGroup(group);
                                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                                    socket.send(packet);
                                    socket.close();

                                    break;
                                }
                                else{
                                    System.out.println("Music exists, connection allowed");
                                }
                            }
                            else{
                                System.out.println("Error checking database");
                                //Send null ip to user, not allowed
                                data.put("feature", String.valueOf(Request.OPEN_TCP));
                                data.put("username", user);
                                data.put("address", null);
                                data.put("musicName", musicName);

                                byte[] buffer = s.serialize(data);

                                InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                                socket.joinGroup(group);
                                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                                socket.send(packet);
                                socket.close();

                                break;
                            }

                            //Get this machine's server
                            InetAddress ip = InetAddress.getLocalHost();
                            String ipString = ip.getHostAddress();
                            System.out.println("This machine address: " + ipString);

                            Map<String, Object> data = new HashMap<>();

                            data.put("feature", String.valueOf(Request.OPEN_TCP));
                            data.put("username", user);
                            data.put("address", ipString);
                            data.put("musicName", musicName);

                            byte[] buffer = s.serialize(data);

                            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                            socket.joinGroup(group);
                            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                            socket.send(packet);

                            //Wait for user to connect, using a timeout
                            try{
                                ServerSocket serverSocket = new ServerSocket(PORT_TCP);
                                serverSocket.setSoTimeout(TCP_LISTEN_TIMEOUT);

                                while (true){
                                    Socket client = serverSocket.accept();

                                    if (client.getLocalAddress().getHostAddress().matches(clientIp)){ //If it's the correct client connecting
                                        //Receive data
                                        ObjectInputStream inFromClient =
                                                new ObjectInputStream(client.getInputStream());
                                        Object file = null;
                                        try{
                                            file = inFromClient.readObject();
                                        } catch (EOFException e){
                                            System.out.println("Finished reading object");
                                        }

                                        System.out.println("User uploaded: " + file);

                                        //Convert byte array back to a file
                                        MusicFile musicFile = (MusicFile) file;
                                        FileOutputStream outFile = new FileOutputStream("music/" + musicName + ".txt");
                                        if (musicFile == null){
                                            System.out.println("Music file is null");
                                            sendCallback(user, "Error uploading file", null);
                                        }
                                        else{
                                            outFile.write(musicFile.fileContent);
                                        }

                                        outFile.close();

                                        client.close();
                                        serverSocket.close();

                                        //Now add the file's location to the database
                                        sql = "UPDATE Music SET uri=\"music/" + musicName + ".txt\" WHERE name=\"" + musicName + "\"";
                                        databaseAccess(user, sql, false, "", Request.UPLOAD_MUSIC);
                                        rc = Integer.parseInt((String)databaseReply(user, Request.UPLOAD_MUSIC));

                                        if (rc==-1){
                                            System.out.println("Error updating file url in database");
                                            sendCallback(user, "File not uploaded", null);
                                            File file1 = new File("music/" + musicName + ".txt");
                                            Files.deleteIfExists(file1.toPath());
                                        }
                                        else{
                                            sendCallback(user, "File uploaded", null);
                                        }

                                        break;
                                    }
                                }
                            } catch (SocketTimeoutException e){
                                System.out.println("Timeout listening to user");
                                sendCallback(user, "Upload timed out", null);
                            } catch (Exception e){
                                e.printStackTrace();
                            }
                        }
                }
            }
        } catch (Exception e){
            System.out.println("Server aborted packet processing unexpectedly");
        }
    }

    /**
     * Performs a search on the database, using a given string as the search parameters.
     * First step is to split the action string, which is a concatenated string of Request codes. The codes represent which attribute
     * to search with, like name, genre of an album, or music artist.
     * <p>
     * Following this, the method iterates on the produced String array, building the SQL query depending on
     * which Request codes are used.
     * <p>
     * Last step is to request a database access using {@link #databaseAccess(String, String, boolean, String, int)} and a
     * database reply using {@link #databaseReply(String, int)}.
     * @param user the user that requested the search
     * @param action concatenated string of Request codes and attribute values
     * @return the database query results in String format, which can be null if no results were found
     */
    private String searchHandler(String user, String action) {
        try{
            //Split the action string
            //System.out.println("Action: " + action);
            String[] splitString = action.split("_");
            //Get the table in which to search
            String table;
            int code = Integer.parseInt(splitString[0]);
            if (code==Request.SEARCH_ALBUM){
                table = "Albums";
            }
            else if (code== Request.SEARCH_ARTIST){
                table = "Artists";
            }
            else table = "Music";

            //Get search parameters
            String sqlQuery = "SELECT name FROM " + table + " WHERE ";
            for(int i=1; i<splitString.length; i+=2){
                if (Integer.parseInt(splitString[i])==Request.SEARCH_BY_NAME){
                    sqlQuery = sqlQuery.concat("name LIKE \'%"+splitString[i+1]+"%\'");
                }
                else if (Integer.parseInt(splitString[i])==Request.SEARCH_BY_ALBUM){
                    sqlQuery = sqlQuery.concat("album LIKE \'%"+splitString[i+1]+"%\'");
                }
                else if (Integer.parseInt(splitString[i])==Request.SEARCH_BY_ARTIST){
                    sqlQuery = sqlQuery.concat("artist LIKE \'%"+splitString[i+1]+"%\'");
                }
                else if (Integer.parseInt(splitString[i])==Request.SEARCH_BY_GENRE){
                    sqlQuery = sqlQuery.concat("genre LIKE \'%"+splitString[i+1]+"%\'");
                }
                if ((i+2)<splitString.length) sqlQuery = sqlQuery.concat(" AND ");
            }
            sqlQuery = sqlQuery.concat(";");
            System.out.println("SQL produced is: " + sqlQuery);

            //Ask for database handler to execute query
            databaseAccess(user, sqlQuery, true, "name", Request.SEARCH);
            //Wait for database reply
            return (String) databaseReply(user, Request.SEARCH);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Handles all interaction with database in this class.
     * <p>
     * When called, all the parameters passed are packed into a HashMap and sent via multicast to DBConnection,
     * which will then execute the given SQL query as parameter, and return the results via another UDP multicast datagram.
     * <p>
     * Results returned are dependent on the query type, if it is a SELECT statement, and the wanted columns given as parameter,
     * which are a String of this format, column1_column2_..._columnN.
     * @param user user that requested the database access
     * @param sqlQuery the SQL command to execute
     * @param isQuery if it's a SELECT command or not
     * @param columns columns to be returned to the user for each row queried
     * @param feature feature requested by the user, mainly to identify which database reply belongs to who in
     *                a simultaneous user situation
     * @throws IOException
     */
    private void databaseAccess(String user, String sqlQuery, boolean isQuery, String columns, int feature) throws IOException {
        MulticastSocket socket = new MulticastSocket();
        Map<String, Object> data = new HashMap<>();

        data.put("server", serverNumber);
        data.put("feature", String.valueOf(Request.DB_ACCESS));
        data.put("feature_requested", feature);
        data.put("username", user);
        data.put("sql", sqlQuery);
        data.put("isquery", isQuery);
        data.put("columns", columns);

        byte[] buffer = s.serialize(data);

        //Create udp packet
        InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT_DBCONNECTION);
        socket.send(packet);
    }

    /**
     * Receives all replies from the database in this class.
     * <p>
     * Waits for a UDP datagram to come from the DBConnection on the class's multicast address on a specific port for
     * database replies.
     * <p>
     * Uses the name of the user who requested the database access, the feature that was requested and the server number
     * to check which reply belongs to what user request.
     * @param user the user that requested the database access
     * @param feature_requested the feature that the user requested
     * @return an Object with the query results, this can be a string with multiple row information, or a simple integer
     *          reporting the success of the query, depending exactly on which feature was requested
     */
    @SuppressWarnings("unchecked")
    private Object databaseReply(String user, int feature_requested){
        try{
            MulticastSocket socket = new MulticastSocket(PORT_DB_ANSWER);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);

            byte[] buffer = new byte[8192];

            DatagramPacket packetIn = new DatagramPacket(buffer, buffer.length);
            socket.receive(packetIn);

            Map<String, Object> data = (Map<String, Object>) s.deserialize(packetIn.getData());

            //Check if response is the one we want
            if (Integer.parseInt((String)data.get("server")) == serverNumber //If this server requested it
                    && ((String)data.get("username")).matches(user) //And it's for this user
                    && Integer.parseInt((String)data.get("feature_requested")) == feature_requested){ //And its the wanted feature
                //It's our response
                return data.get("results");
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Sends single notification immediately to given user if said user is online over UDP. If user is not
     * online, then notification is saved in the database
     * @param targetUser the user which the notification is meant to
     * @param user user responsible for triggering notification
     * @param edit a small description of the edited resource,
     *             in the case the notification informs about a new edit
     * @param code the Request code associated with edit type, if an edit was made
     */
    private void sendSingleNotification(String targetUser, String user, String edit, int code) {
        try{
            //Check if the user is online
            int rc = checkLoginState(targetUser);

            if (rc==NO_USER_FOUND){
                return;
            }
            else if (rc==NO_LOGIN){
                //Not logged in, save the notification in the db
                //Get existing notifications
                String all_notes = null;
                all_notes = getAllNotes(targetUser);
                //Add new note to the list
                String new_note = "";

                //If not first note, add separator
                if (all_notes!=null){
                    new_note = new_note.concat(" | ");
                }

                if (code==Request.NOTE_EDITOR){
                    new_note = new_note.concat(new_editor_note + user);
                }
                else if (code==Request.NOTE_NEW_EDIT){
                    new_note = new_note.concat(new_edit + edit);
                }

                if (all_notes != null){
                    all_notes = all_notes.concat(new_note);
                }
                else{
                    all_notes = new_note;
                }

                //Update value in database
                String sql = "UPDATE Users SET notes=\""
                        + all_notes + "\" WHERE name=\"" + targetUser + "\";";
                //Request database access
                databaseAccess(user, sql, false, "", Request.NOTE_EDITOR);
                rc = Integer.parseInt((String) databaseReply(user, Request.NOTE_EDITOR));

                if (rc==-1) System.out.println("Error saving notifications");
                else System.out.println("Saved notifications of user: " + targetUser);
            }
            else{
                //User is logged in
                //Create packet and send it
                //Create multicast socket
                MulticastSocket socket = new MulticastSocket();

                //Create data map
                Map<String, Object> callback = new HashMap<>();
                callback.put("feature", String.valueOf(Request.NOTE_EDITOR));
                callback.put("username", targetUser);

                byte[] buffer = s.serialize(callback);

                //Create udp packet
                InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                socket.send(packet);
            }

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends given string of concatenated notifications to user passed as parameter over UDP
     * @param user the user to which the notifications are meant
     * @param notes concatenated notifications
     */
    private void sendMultipleNotifications(String user, String notes){
        try{
            //Create packet and send it
            //Create multicast socket
            MulticastSocket socket = new MulticastSocket();

            //Create data map
            Map<String, Object> callback = new HashMap<>();
            callback.put("feature", String.valueOf(Request.NOTE_DELIVER));
            callback.put("username", user);
            callback.put("notes", notes);

            byte[] buffer = s.serialize(callback);

            //Create udp packet
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
            socket.send(packet);

            //Clear the notes
            String sql = "UPDATE Users SET notes=null WHERE name=\"" + user + "\";";
            databaseAccess(user,sql, false, "", Request.CLEAR_NOTES);

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets all the pending notifications of user passed as parameter
     * @param user
     * @return a single string with all the notifications concatenated, separated by '|' character.
     *          String is null if there are no notifications to get
     */
    private String getAllNotes(String user) {
        try{
            String sql = "SELECT notes FROM Users WHERE name=\"" + user + "\";";

            databaseAccess(user, sql, true, "notes", Request.GET_NOTES);
            //Wait for answer
            String results = (String) databaseReply(user, Request.GET_NOTES);
            //Split string
            if (results != null){
                String[] tokens = results.split("_");
                //Format example: 1_notes_Note 1 | Note number two | Another one_
                if (tokens.length < 2){
                    System.out.println("NO RESULT RECEIVED");
                    return null;
                }
                else{
                    return tokens[2];
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * First method to be called if the user requests an edit, an addition or a removal.
     * <p>
     * First step is to parse the string received in the UDP datagram into 4 different strings if it is an edit,
     * or 2 strings if it is a removal or an addition:
     * <u>
     *     <li><b>Code:</b> determines which type of resource to edit, music, album or artist, or if it is an addition/removal</li>
     *     <li><b>(rem/add)Name:</b> name of the item that is to be added or removed</li>
     *     <li><b>(edit)Attribute:</b> which field of that resource it is to be changed</li>
     *     <li><b>(edit)Name:</b> which resource the user wants to change, for example, name of the album</li>
     *     <li><b>(edit)NewValue:</b> new value of that field</li>
     * </u>
     * <p>
     * If an edit was requested, it then calls {@link #attributeEdit(int, String, String, String)} that will change that attribute
     * in the database
     * <p>
     * For an addition, {@link #addItem(String, String)} is called, which first checks if same name already exists and then adds the item.
     * <p>
     * For a removal, {@link #removeItem(String, String)} is called, which first checks if the item exists and then removes it from the database.
     * @param user editor that requested the change
     * @param action string that holds command
     * @return an integer. DB_EXCEPTION if there was a SQL related exception, -1 if unsuccessful edit,
     * -2 if unsuccessful addition, -3 if unsuccessful removal, 0 if successful edit,
     * 1 if successful addition and 2 if successful removal
     */
    private int managerHandler(String user, String action){
        try{
            //Parse action string
            String[] actionSplit = action.split("_");

            //Request code, could be an edit or an addition
            int code = Integer.parseInt(actionSplit[0]);

            //Check if it is an addition, a removal or an edit and if it is on album, music or artist
            switch(code){
                case Request.EDIT_MUSIC:
                case Request.EDIT_ALBUM:
                case Request.EDIT_ARTIST:
                    //Get which attribute to update
                    int attribute = Integer.parseInt(actionSplit[1]);
                    String name = actionSplit[2]; //Name of the item (album, music or artist)
                    String newValue = actionSplit[3]; //New value of the attribute
                    String table;
                    if (code==Request.EDIT_MUSIC) table = "Music";
                    else if (code== Request.EDIT_ALBUM) table = "Albums";
                    else table = "Artists";
                    return attributeEdit(attribute, name, newValue, table);
                case Request.ADD_ALBUM:
                case Request.ADD_ARTIST:
                case Request.ADD_MUSIC:
                    name = actionSplit[1];
                    if (code== Request.ADD_ALBUM) table = "Albums";
                    else if (code==Request.ADD_ARTIST) table = "Artists";
                    else table = "Music";
                    return addItem(name, table);
                case Request.REMOVE_ALBUM:
                case Request.REMOVE_ARTIST:
                case Request.REMOVE_MUSIC:
                    name = actionSplit[1];
                    if (code== Request.REMOVE_ALBUM) table = "Albums";
                    else if (code== Request.REMOVE_ARTIST) table = "Artists";
                    else table = "Music";
                    return removeItem(name, table);
            }

            return -1;
        } catch(Exception e){
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Adds item to database with given name as parameter into given table
     * @param name
     * @param table
     * @return an integer. DB_EXCEPTION if there was a SQL related exception, -2 if item wasn't added and 1 otherwise
     */
    private int addItem(String name, String table) {
        // TODO (optional) Allow more parameters when creating new album or artist
        try{
            //Check if same name already exists
            int rc = checkIfNameExists(name, table);
            if (rc==-1){
                System.out.println("Name already exists");
                return -2;
            }
            else if (rc==0){
                //Name of the field is to be changed
                String sql = "INSERT INTO \"" + table + "\" (name) VALUES (\"" + name + "\");";
                databaseAccess(name, sql, false, "", Request.ADD_ITEM);
                rc = Integer.parseInt((String)databaseReply(name, Request.ADD_ITEM));
                if (rc==-1){
                    System.out.println("Error adding item to database");
                    return -2;
                }
            }
            return 1;
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Removes item with given name as parameter in given table from the database, first checking if it exists
     * @param name
     * @param table
     * @return an integer. DB_EXCEPTION if there was a SQL related exception, -3 if item wasn't removed and 2 otherwise
     */
    private int removeItem(String name, String table){
        try{
            //Check if same name already exists
            int rc = checkIfNameExists(name, table);
            if (rc==-1){
                //Name of the field is to be changed
                String sql = "DELETE FROM \"" + table + "\" WHERE name=\"" + name + "\";";
                databaseAccess(name, sql, false, "", Request.REMOVE_ITEM);
                rc = Integer.parseInt((String)databaseReply(name, Request.REMOVE_ITEM));
                if (rc==-1){
                    System.out.println("Error removing item from database");
                    return -3;
                }

                return 2;
            }
            else if (rc==0){
                System.out.println("Item doesn't exist");
                return -3;
            }
            else return -3;
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Edits given attribute as parameter in the database of given item name. Attribute can be a name, birth year or genre,
     * also receives which table to change as parameter
     * @param attribute column in the database to change
     * @param name name of the item in the table
     * @param newValue new value of the attribute
     * @param table table in which to change the attribute
     * @return an integer. DB_EXCEPTION if there was a SQL related exception, -1 if the field was not changed for whatever reason and
     * 0 if successful
     */
    private int attributeEdit(int attribute, String name, String newValue, String table) {
        try{
            //Check if attribute is to be edited
            int rc = 0;
            switch (attribute){
                case Request.EDIT_NAME:
                    //Check if the new name doesn't already exists
                    rc = checkIfNameExists(newValue, table);
                    if (rc==-1){
                        System.out.println("Name already exists");
                        return rc;
                    }
                    else if (rc==0){
                        //Name of the field is to be changed
                        String sql = "UPDATE " + table + " SET \"" + DB_FIELD_NAME
                                +  "\"=\"" + newValue + "\" WHERE \"" + DB_FIELD_NAME + "\"=\"" + name + "\"";
                        databaseAccess("name", sql, false, "", Request.ATTRIBUTE_EDIT);
                    }
                    break;
                case Request.EDIT_BIRTH:
                    //Artist birth year is to be changed
                    String sql = "UPDATE " + table + " SET \"" + DB_FIELD_BIRTH
                            +  "\"=\"" + newValue + "\" WHERE \"" + DB_FIELD_NAME + "\"=\"" + name + "\"";
                    databaseAccess("name", sql, false, "", Request.ATTRIBUTE_EDIT);
                    break;
                case Request.EDIT_GENRE:
                    //Album genre is to be changed
                    sql = "UPDATE " + table + " SET \"" + DB_FIELD_GENRE
                            +  "\"=\"" + newValue + "\" WHERE \"" + DB_FIELD_NAME + "\"=\"" + name + "\"";
                    databaseAccess("name", sql, false, "", Request.ATTRIBUTE_EDIT);
                    break;
                case Request.EDIT_LYRICS:
                    //Music lyrics
                    sql = "UPDATE " + table + " SET \"" + DB_FIELD_LYRICS
                            +  "\"=\"" + newValue + "\" WHERE \"" + DB_FIELD_NAME + "\"=\"" + name + "\"";
                    databaseAccess("name", sql, false, "", Request.ATTRIBUTE_EDIT);
                    break;
                case Request.EDIT_DESCRIPTION:
                    //Album descritpion
                    sql = "UPDATE " + table + " SET \"" + DB_FIELD_DESCRIPTION
                            +  "\"=\"" + newValue + "\" WHERE \"" + DB_FIELD_NAME + "\"=\"" + name + "\"";
                    databaseAccess("name", sql, false, "", Request.ATTRIBUTE_EDIT);
                    break;
                case Request.EDIT_YEAR:
                    //Album or music year
                    sql = "UPDATE " + table + " SET \"" + DB_FIELD_YEAR
                            +  "\"=\"" + newValue + "\" WHERE \"" + DB_FIELD_NAME + "\"=\"" + name + "\"";
                    databaseAccess("name", sql, false, "", Request.ATTRIBUTE_EDIT);
                    break;
                case Request.EDIT_FIELD_ALBUMS:
                case Request.EDIT_FIELD_ARTIST:
                    //Albums field in music | Artist field in albums and music
                    //Check if new album or artist exists in database and returns it's id
                    int id = (attribute==Request.EDIT_FIELD_ALBUMS ? checkIfAlbumExists(newValue) : checkIfArtistExists(newValue));
                    if (id==-1){
                        return -1;
                    }
                    else{
                        //Update the value
                        sql = "UPDATE " + table + " SET \""
                                + (attribute==Request.EDIT_FIELD_ALBUMS ? DB_FIELD_ALBUM : DB_FIELD_ARTIST)
                                +  "\"=\"" + newValue + "\" WHERE \"" + DB_FIELD_NAME + "\"=\"" + name + "\"";
                        databaseAccess("name", sql, false, "", Request.ATTRIBUTE_EDIT);
                        return 0;
                    }
            }

            if (rc==-1){
                System.out.println("Error updating value");
                return -1;
            }
            else{
                System.out.println("Field edited");
                return 0;
            }
        } catch (Exception e){
            e.printStackTrace();
        }

        return -1;
    }

    /**
     * Checks if a given name of an item in a given table as parameter already exists in the database
     * @param name
     * @param table
     * @return an integer. DB_EXCEPTION if there was a SQL related exception, -1 if item already exists, 0 otherwise
     */
    private int checkIfNameExists(String name, String table){
        try{
            String sql = "SELECT name FROM \"" + table + "\" WHERE name=\"" + name + "\";";
            databaseAccess(name, sql, true, "name", Request.CHECK_USER_EXISTS);
            String results = (String) databaseReply(name, Request.CHECK_USER_EXISTS);
            if (results != null){
                String[] tokens = results.split("_");
                if(tokens.length < 2){
                    System.out.println("No item with given name found");
                    return 0;
                }

                return -1;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Simple method that checks if a given artist as parameter exists in the database
     * @param artist
     * @return an integer. DB_EXCEPTION if there was a SQL related exception, -1 if the artist was not found
     * and the artist's id in the database if successful
     */
    private int checkIfArtistExists(String artist) {
        try{
            String sql = "SELECT id FROM Artists WHERE name=\"" + artist + "\";";
            databaseAccess(artist, sql, true, "id", Request.CHECK_USER_EXISTS);
            String results = (String) databaseReply(artist, Request.CHECK_USER_EXISTS);
            if (results != null){
                String[] tokens = results.split("_"); //Format: 1_id_245
                if(tokens.length < 2){
                    System.out.println("No artist with given name found");
                    return -1;
                }


                return Integer.parseInt(tokens[2]);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Simple method that checks if a given album as parameter exists in the database
     * @param album
     * @return an integer. DB_EXCEPTION if there was a SQL related exception, -1 if the album was not found
     * and the album's id in the database if successful
     */
    private int checkIfAlbumExists(String album) {
        try{
            String sql = "SELECT id FROM Albums WHERE name=\"" + album + "\";";
            databaseAccess(album, sql, true, "id", Request.CHECK_USER_EXISTS);
            String results = (String) databaseReply(album, Request.CHECK_USER_EXISTS);
            if (results != null){
                String[] tokens = results.split("_"); //Format: 1_id_245
                if(tokens.length < 2){ //Format: 1_id_245
                    System.out.println("No album with given name found");
                    return -1;
                }

                return Integer.parseInt(tokens[2]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Registers user passed as parameter, saving its name and password in the database
     * First user to register is automatically made editor
     * @param user
     * @param pass
     * @return the integer codes defined in the class. DB_EXCEPTION if there was a SQL related exception,
     *          -1 if user already exists and 0 if successful
     */
    private int registerHandler(String user, String pass) {
        try{
            //Check if user already exists
            int rc = checkIfUserExists(user);
            if (rc!=NO_USER_FOUND){
                System.out.println("User already exists/database error");
                return -1;
            }

            String editorState = "0"; //If first user, editor state is set to 1
            //Checks if it is first user
            String sql = "SELECT name FROM Users;";
            databaseAccess(user, sql, true, "name", Request.CHECK_USER_EXISTS);
            String results = (String)databaseReply(user, Request.CHECK_USER_EXISTS);
            if (results != null){
                String[] tokens = results.split("_"); // 1_name_user1
                if(tokens.length < 2){
                    //First user being added
                    System.out.println("First user");
                    editorState = "1"; //First user, first editor
                }

                //Add the user to the database
                sql = "INSERT INTO Users " +
                        "(name, password, login, editor) " +
                        "VALUES (\"" + user +"\",\"" + pass + "\",\"0\",\"" + editorState + "\");";
                databaseAccess(user, sql, false, "", Request.REGISTER_USER);
                rc = Integer.parseInt((String)databaseReply(user, Request.REGISTER_USER));

                if (rc==-1){
                    System.out.println("Error adding user");
                    return -1;
                }
                else{
                    System.out.println("Added user");
                    return 0;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Changes editor privileges of the user passed as parameter
     * @param editor User that requested the change
     * @param newEditor User that is to be made editor
     * @return the integer codes defined in the class. NO_USER_FOUND if one of the users doesn't exist,
     *          DB_EXCEPTION if there was a SQL related exception and 0 if successful.
     */
    @SuppressWarnings("unchecked")
    private int makeEditorHandler(String editor, String newEditor) {
        try{
            //Check if user to be made editor exists
            int rc = checkIfUserExists(newEditor);

            if (rc==NO_USER_FOUND){

                return NO_USER_FOUND;
            }
            else{
                //Update the value
                String sql = "UPDATE Users SET editor=\"1\" WHERE name=\"" + newEditor + "\";";
                databaseAccess(editor, sql, false, "", Request.MAKE_EDITOR);
                rc = Integer.parseInt((String)databaseReply(editor, Request.MAKE_EDITOR));

                if (rc==-1){
                    System.out.println("ERROR MAKING EDITOR");
                    return -1;
                }
                else{
                    System.out.println("MADE EDITOR");
                    return 0;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Utility function that queries the database looking for the username passed as parameter
     * @param user
     * @return the integer codes defined in the class. NO_USER_FOUND if no user with that name exists,
     *          DB_EXCEPTION if there was a SQL related exception and 0 if a user was found
     */
    private int checkIfUserExists(String user) {
        try{
            String sql = "SELECT name FROM Users WHERE name=\"" + user + "\";";
            databaseAccess(user, sql, true, "name", Request.CHECK_USER_EXISTS);
            //Wait for reply
            String results = (String)databaseReply(user, Request.CHECK_USER_EXISTS);
            System.out.println("(checkIfUserExists) results: " + results);
            if (results != null){
                String[] tokens = results.split("_");
                if(tokens.length < 2){
                    System.out.println("USER NOT FOUND");
                    return NO_USER_FOUND;
                }

                return 0;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Returns an UDP packet back to the client
     * @param user Name of the user that sent the request
     * @param resposta Description of the callback
     * @param optional Optional object
     * @throws IOException
     */
    private void sendCallback(String user, String resposta, Object optional) throws IOException {
        // TODO send back the original feature
        //Create multicast socket
        MulticastSocket socket = new MulticastSocket();

        //Create data map
        Map<String, Object> callback = new HashMap<>();
        callback.put("feature", String.valueOf(Request.CALLBACK));
        callback.put("username", user);
        callback.put("answer", resposta);
        callback.put("optional", optional);

        byte[] buffer = s.serialize(callback);

        //Create udp packet
        InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
        socket.send(packet);
    }

    /**
     * Utility function that checks for the given username as a parameter in the database
     * @param user
     * @return the integer codes defined in the class. NO_USER_FOUND if no user with that name exists,
     *          DB_EXCEPTION if there was a SQL related exception, NO_LOGIN if the user is not logged in
     *          and 0 it is logged in
     */
    private int checkLoginState(String user){
        try{
            String sql = "SELECT login, timestamp FROM Users WHERE name=\"" + user + "\";";
            databaseAccess(user, sql, true, "login_timestamp", Request.CHECK_LOGIN_STATE);
            String results = (String) databaseReply(user, Request.CHECK_LOGIN_STATE);

            if (results != null){
                //Split string
                String[] tokens = results.split("_"); //Format: 2_login_1_timestamp_1556774
                if(tokens.length < 2){
                    System.out.println("NO USER FOUND");
                    return NO_USER_FOUND;
                }

                if(tokens[2].matches("1")){
                    //Logged in, now check if session should be timed out
                    long unixTime = System.currentTimeMillis() / 1000L;
                    //If the difference between now seconds and timestamp is bigger than 1800 seconds (30 minutes)
                    if((unixTime - Long.parseLong(tokens[4])) > 1800){
                        //Set login state to logged out
                        sql = "UPDATE Users SET login=\"0\" WHERE name=\"" + user + "\";";
                        databaseAccess(user, sql, false, "", Request.TIMEOUT);
                        return TIMEOUT;
                    }

                    return 0;
                }
                else{
                    return NO_LOGIN;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        return -1;
    }

    /**
     * Utility function that checks if the given user as parameter has editor privileges.
     * It assumes that the user exists
     * @param user
     * @return the integer codes defined in the class. DB_EXCEPTION if there was a SQL related exception,
     *          NOT_EDITOR if user failed privilege check, 0 if user is editor
     */
    private int checkIfEditor(String user){
        try{
            String sql = "SELECT editor FROM Users WHERE name=\"" + user + "\";";
            databaseAccess(user, sql, true, "editor", Request.CHECK_EDITOR_STATE);
            String results = (String) databaseReply(user, Request.CHECK_EDITOR_STATE);

            if (results != null){
                String[] tokens = results.split("_"); //Format: 1_editor_0
                if(tokens[2].matches("1")){
                    return 0;
                }
                else{
                    return NOT_EDITOR;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Handles login and logout for the user and password given as parameters
     * @param user
     * @param password
     * @param code Depending on this parameter's value, handle login or logout
     * @return the integer codes defined in the class. NO_USER_FOUND if the user was not found in the database,
     *          ALREADY_LOGIN if the user is already logged in, -1 if wrong username/password combination
     *          and 0 for a successful login or logout
     */
    @SuppressWarnings("unchecked")
    private int loginHandler(String user, String password, int code){
        try{
            //Check login state
            String sql = "SELECT * FROM Users WHERE name=\"" + user + "\";";
            //Request database query
            databaseAccess(user, sql, true, "name_login_timestamp", Request.NAME_EXISTS);
            //Wait for database answer
            String result = (String) databaseReply(user, Request.NAME_EXISTS);
            //Split results
            if (result != null){
                String[] tokens = result.split("_");
                //Check if name exists

                if(tokens.length < 2){ //Only got number of columns of the query
                    System.out.println("NO USER FOUND");
                    return NO_USER_FOUND;
                }

                if (code==Request.LOGIN){ //User wants to login
                    boolean alreadyLogin = tokens[4].matches("1"); //Format, example: 3_name_user1_login_1_timestamp_15538223
                    if (alreadyLogin){
                        System.out.println("ALREADY LOGIN");
                        return ALREADY_LOGIN;
                    }
                    else{ //Not yet logged in
                        //Updates login state and timestamp
                        long unixTime = System.currentTimeMillis() / 1000L;
                        //Request database access
                        sql = "UPDATE Users SET login=\"1\", timestamp=\"" + unixTime + "\" WHERE name=\""
                                + user + "\" AND password=\"" + password + "\";";
                        databaseAccess(user, sql,false,"", Request.UPDATE_LOGIN_STATE);
                        //Wait for database reply
                        int rc = Integer.parseInt((String)databaseReply(user, Request.UPDATE_LOGIN_STATE));
                        if(rc==0){
                            System.out.println("NO LOGIN");
                            return -1;
                        }
                        else{
                            System.out.println("LOGIN");
                            return 0;
                        }
                    }
                }
                else{ //User wants to logout
                    sql = "UPDATE Users SET login=\"0\" WHERE name=\""
                            + user + "\";";
                    databaseAccess(user,sql,false,"", Request.UPDATE_LOGIN_STATE);
                    int rc = Integer.parseInt((String)databaseReply(user, Request.UPDATE_LOGIN_STATE));

                    System.out.println("LOGOUT");
                    return 0;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }
}
