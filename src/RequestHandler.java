
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.sql.*;
import java.util.*;

/**
 * Runnable that handles a single UDP datagram
 */
public class RequestHandler implements Runnable {
    private static String MULTICAST_ADDRESS = "224.3.2.1";
    private static int PORT = 4321;

    private static DatagramPacket clientPacket;
    private static Map<String, Object> data = null;

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


    /**
     * Constructor for request handler, uses the UDP datagram received by the server and a database
     * connection to serve the request
     * @param packet UDP datagram received
     * @param connection Database connection passed by the server, this connection object is shared
     *                   between all request handlers, in order to solve synchronization issues
     */
    RequestHandler(DatagramPacket packet, Connection connection, int serverNumber){
        this.clientPacket = packet;
        this.connection = connection;
        this.serverNumber = serverNumber;
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

        if (data != null){
            //Check if server should be handling this packet
            int server = Integer.parseInt((String)data.get("server"));
            if(server != serverNumber){
                System.out.println("Server " + serverNumber + " aborted packet processing");
                return;
            }
            //Check which feature user wants to do
            int code = Integer.parseInt((String)data.get("feature"));
            switch(code){
                case Request.REGISTER:
                    try{
                        String user = (String) data.get("username");
                        String pass = (String) data.get("password");

                        int rc = registerHandler(user, pass);

                        if (rc==NO_USER_FOUND || rc==-1) sendCallback(user, "User already exists", null);
                        else if (rc==DB_EXCEPTION) sendCallback(user, "Database error", null);
                        else sendCallback(user, "User registered", null);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
        //----------------------------------------------------------------------------------
                case Request.LOGIN:
                case Request.LOGOUT:
                    // TODO update timestamp on login even if user already is logged in
                    try{
                        System.out.println("User wants to login/logout");
                        String user = (String)data.get("username");
                        String password = (String) data.get("password");

                        //Check in db
                        int rc = loginHandler(user, password, code);

                        if (code == Request.LOGIN){
                            if (rc==ALREADY_LOGIN) sendCallback(user, "User already logged in", null);
                            else if (rc==NO_USER_FOUND) sendCallback(user, "User not found", null);
                            else if (rc==DB_EXCEPTION) sendCallback(user, "Database error", null);
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
                            else if (rc==DB_EXCEPTION) sendCallback(user, "Database error", null);
                            else sendCallback(user, "User logged out", null);
                        }

                    } catch ( IOException e) {
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
                        if(rc==NO_USER_FOUND) sendCallback(editor, "User not found", null);
                        else if (rc==DB_EXCEPTION) sendCallback(editor, "Database error", null);
                        else if(rc==NO_LOGIN) sendCallback(editor, "User is not logged in", null);
                        else if(rc==TIMEOUT) sendCallback(editor, "Session login timed out", null);
                        else{
                            //Check if is editor
                            rc = checkIfEditor(editor);
                            if (rc==NOT_EDITOR) sendCallback(editor, "User is not editor", null);
                            else if (rc==DB_EXCEPTION) sendCallback(editor, "Database error", null);
                            else{
                                //Make editor
                                rc = makeEditorHandler(editor, newEditor);
                                if (rc==NO_USER_FOUND) sendCallback(editor, "New editor not found", null);
                                else if (rc==DB_EXCEPTION) sendCallback(editor, "Database error", null);
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
                    try{
                        String user = (String) data.get("username");
                        String action = (String) data.get("action");

                        int rc = checkLoginState(user);
                        if(rc==NO_USER_FOUND) sendCallback(user, "User not found", null);
                        else if (rc==DB_EXCEPTION) sendCallback(user, "Database error", null);
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
                                if (rc == DB_EXCEPTION) sendCallback(user, "Database error", null);
                                else if (rc==-1) sendCallback(user, "Field not edited", null);
                                else sendCallback(user, "Field edited", null);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
        }
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
            connect();
            Statement statement = connection.createStatement();

            //Check if the user is online
            int rc = checkLoginState(targetUser);
            connect();

            if (rc==NO_USER_FOUND){
                connection.close();
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
                rc = statement.executeUpdate("UPDATE Users SET notes=\""
                        + all_notes + "\" WHERE name=\"" + targetUser + "\";");

                if (rc==-1) System.out.println("Error saving notifications");
                else System.out.println("Saved notifications of user: " + targetUser);
            }
            else if (rc==DB_EXCEPTION){
                connection.close();
                System.out.println("Error sending notification");
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

        } catch (SQLException e) {
            System.out.println("ERROR SENDING NOTIFICATION");
            e.printStackTrace();
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
            connect();
            Statement statement = connection.createStatement();

            int rc = checkIfUserExists(user);

            if (rc==NO_USER_FOUND){
                connection.close();
                return;
            }
            else if (rc==DB_EXCEPTION){
                System.out.println("Failed to send all notifications");
                connection.close();
                return;
            }
            else{
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
            }

            //Clear the notes
            statement.executeUpdate("UPDATE Users SET notes=null WHERE name=\"" + user + "\";");
            connection.close();

        } catch (SQLException e) {
            e.printStackTrace();
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
            connect();
            Statement statement = connection.createStatement();

            ResultSet rs = statement.executeQuery("SELECT notes FROM Users WHERE name=\""
                    + user + "\";");

            if (!rs.next()){
                connection.close();
                System.out.println("NO RESULT RECEIVED");
                return null;
            }
            else{
                String notes = rs.getString("notes");
                connection.close();
                return notes;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    private int managerHandler(String user, String action){
        try{
            //Parse action string
            String[] actionSplit = action.split("_");

            //Get which attribute to update
            int attribute = Integer.parseInt(actionSplit[1]);
            String name = actionSplit[2]; //Name of the item (album, music or artist)
            String newValue = actionSplit[3]; //New value of the attribute

            //Check if edit is on album, music or artist
            int code = Integer.parseInt(actionSplit[0]);
            switch(code){
                case Request.EDIT_MUSIC:
                    return attributeEdit(attribute, name, newValue, "Music");
                case Request.EDIT_ALBUM:
                    return attributeEdit(attribute, name, newValue, "Albums");
                case Request.EDIT_ARTIST:
                    return attributeEdit(attribute, name, newValue, "Artists");
            }

            return -1;
        } catch(Exception e){
            e.printStackTrace();
            return -1;
        }
    }

    private int attributeEdit(int attribute, String name, String newValue, String table) {
        System.out.println("Entered attributeEdit");
        try{
            //Check if attribute is to be edited
            connect();
            Statement statement = connection.createStatement();
            int rc = -1;
            switch (attribute){
                // TODO prevent new name from being an existing one
                case Request.EDIT_NAME:
                    //Name of the field is to be changed
                    rc = statement.executeUpdate("UPDATE " + table + " SET \"" + DB_FIELD_NAME
                            +  "\"=\"" + newValue + "\" WHERE \"" + DB_FIELD_NAME + "\"=\"" + name + "\"");
                    break;
                case Request.EDIT_BIRTH:
                    //Artist birth year is to be changed
                    rc = statement.executeUpdate("UPDATE " + table + " SET \"" + DB_FIELD_BIRTH
                            +  "\"=\"" + newValue + "\" WHERE \"" + DB_FIELD_NAME + "\"=\"" + name + "\"");
                    break;
                case Request.EDIT_GENRE:
                    //Album genre is to be changed
                    rc = statement.executeUpdate("UPDATE " + table + " SET \"" + DB_FIELD_GENRE
                            +  "\"=\"" + newValue + "\" WHERE \"" + DB_FIELD_NAME + "\"=\"" + name + "\"");
                    break;
                case Request.EDIT_LYRICS:
                    //Music lyrics
                    rc = statement.executeUpdate("UPDATE " + table + " SET \"" + DB_FIELD_LYRICS
                            +  "\"=\"" + newValue + "\" WHERE \"" + DB_FIELD_NAME + "\"=\"" + name + "\"");
                    break;
                case Request.EDIT_DESCRIPTION:
                    //Album descritpion
                    rc = statement.executeUpdate("UPDATE " + table + " SET \"" + DB_FIELD_DESCRIPTION
                            +  "\"=\"" + newValue + "\" WHERE \"" + DB_FIELD_NAME + "\"=\"" + name + "\"");
                    break;
                case Request.EDIT_YEAR:
                    //Album or music year
                    rc = statement.executeUpdate("UPDATE " + table + " SET \"" + DB_FIELD_YEAR
                            +  "\"=\"" + newValue + "\" WHERE \"" + DB_FIELD_NAME + "\"=\"" + name + "\"");
                    break;
                case Request.EDIT_FIELD_ALBUMS:
                case Request.EDIT_FIELD_ARTIST:
                    //Albums field in music | Artist field in albums and music
                    //Check if new album or artist id exists in database and returns it
                    int id = (attribute==Request.EDIT_FIELD_ALBUMS ? checkIfAlbumExists(newValue) : checkIfArtistExists(newValue));
                    connect();
                    if (id==DB_EXCEPTION){
                        System.out.println("Error updating value");
                        connection.close();
                        return DB_EXCEPTION;
                    }
                    else if (id==-1){
                        connection.close();
                        return -1;
                    }
                    else{
                        //Update the value
                        rc = statement.executeUpdate("UPDATE " + table + " SET \""
                                + (attribute==Request.EDIT_FIELD_ALBUMS ? DB_FIELD_ALBUM : DB_FIELD_ARTIST)
                                +  "\"=\"" + id + "\" WHERE \"" + DB_FIELD_NAME + "\"=\"" + name + "\"");
                        connection.close();
                        return rc;
                    }
            }

            if (rc==-1){
                System.out.println("Error updating value");
                connection.close();
                return DB_EXCEPTION;
            }
            else{
                System.out.println("Field edited");
                connection.close();
                return 0;
            }
        } catch (Exception e){
            e.printStackTrace();
        }

        return -1;
    }

    private int checkIfArtistExists(String newValue) {
        try{
            connect();
            Statement statement = connection.createStatement();

            ResultSet rs = statement.executeQuery("SELECT id FROM Artists WHERE name=\"" + newValue + "\";");
            if(!rs.next()){
                System.out.println("No artist with given name found");
                connection.close();
                return -1;
            }

            int id = rs.getInt("id");
            connection.close();
            return id;

        } catch (SQLException e) {
            e.printStackTrace();
            return DB_EXCEPTION;
        }
    }

    private int checkIfAlbumExists(String newValue) {
        try{
            connect();
            Statement statement = connection.createStatement();

            ResultSet rs = statement.executeQuery("SELECT id FROM Albums WHERE name=\"" + newValue + "\";");
            if(!rs.next()){
                System.out.println("No album with given name found");
                return -1;
            }

            int id = rs.getInt("id");
            connection.close();
            return id;

        } catch (SQLException e) {
            e.printStackTrace();
            return DB_EXCEPTION;
        }
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
            connect();
            Statement statement = connection.createStatement();

            //Check if user already exists
            int rc = checkIfUserExists(user);
            if (rc!=NO_USER_FOUND){
                System.out.println("User already exists/database error");
                connection.close();
                return -1;
            }

            connect(); //Connection may have been closed
            String editorState = "0";
            //Checks if it is first user
            ResultSet rs = statement.executeQuery("SELECT * FROM Users;");
            if(!rs.next()){
                //First user being added
                System.out.println("First user");
                editorState = "1";
            }

            //Add the user to the database
            rc = statement.executeUpdate("INSERT INTO Users " +
                    "(name, password, login, editor, has_notifications) " +
                    "VALUES (\"" + user +"\",\"" + pass + "\",\"0\",\"" + editorState + "\",\"0\");");

            connection.close();

            if (rc==-1){
                System.out.println("Error adding user");
                return DB_EXCEPTION;
            }
            else{
                System.out.println("Added user");
                return 0;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return DB_EXCEPTION;
        }
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
            connect();
            Statement statement = connection.createStatement();

            //Check if user to be made editor exists
            int rc = checkIfUserExists(newEditor);
            connect(); //Connection may have been closed
            if (rc==NO_USER_FOUND){
                connection.close();
                return NO_USER_FOUND;
            }
            else{
                //Update the value
                rc = statement.executeUpdate("UPDATE Users SET editor=\"1\" WHERE name=\""
                        + newEditor + "\";");
                connection.close();

                if (rc==-1){
                    System.out.println("ERROR MAKING EDITOR");
                    return DB_EXCEPTION;
                }
                else{
                    System.out.println("MADE EDITOR");
                    return 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return DB_EXCEPTION;
        }
    }

    /**
     * Utility function that queries the database looking for the username passed as parameter
     * @param user
     * @return the integer codes defined in the class. NO_USER_FOUND if no user with that name exists,
     *          DB_EXCEPTION if there was a SQL related exception and 0 if a user was found
     */
    private int checkIfUserExists(String user) {
        try{
            connect();
            Statement statement = connection.createStatement();

            ResultSet rs = statement.executeQuery("SELECT * FROM Users WHERE name=\""
                    + user + "\";");

            if(!rs.next()){
                System.out.println("USER NOT FOUND");
                connection.close();
                return NO_USER_FOUND;
            }

            connection.close();
            return 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return DB_EXCEPTION;
        }
    }

    /**
     * Returns an UDP packet back to the client
     * @param user Name of the user that sent the request
     * @param resposta Description of the callback
     * @param optional Optional object
     * @throws IOException
     */
    private void sendCallback(String user, String resposta, Object optional) throws IOException {
        //Create multicast socket
        MulticastSocket socket = new MulticastSocket();

        //Create data map
        Map<String, Object> callback = new HashMap<>();
        callback.put("feature", String.valueOf(Request.CALLBACK));
        callback.put("username", user);
        callback.put("answer", resposta);
        callback.put("optional", null);

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
            connect();
            Statement statement = connection.createStatement();

            ResultSet rs = statement.executeQuery("SELECT login, timestamp FROM Users WHERE name=\""
                    + user + "\";");

            if(!rs.next()){
                System.out.println("NO USER FOUND");
                connection.close();
                return NO_USER_FOUND;
            }

            if((rs.getString("login")).matches("1")){
                //Logged in, now check if session should be timed out
                long unixTime = System.currentTimeMillis() / 1000L;
                //If the difference between now seconds and timestamp is bigger than 1800 seconds (30 minutes)
                if((unixTime - rs.getLong("timestamp")) > 1800){
                    //Set login state to logged out
                    statement.executeUpdate("UPDATE Users SET login=\"0\" WHERE name=\""
                            + user + "\";");
                    connection.close();
                    return TIMEOUT;
                }

                connection.close();
                return 0;
            }
            else{
                connection.close();
                return NO_LOGIN;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return DB_EXCEPTION;
        }
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
            connect();
            Statement statement = connection.createStatement();

            ResultSet rs = statement.executeQuery("SELECT editor FROM Users WHERE name=\""
                    + user + "\";");

            if((rs.getString("editor")).matches("1")){
                connection.close();
                return 0;
            }
            else{
                connection.close();
                return NOT_EDITOR;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return DB_EXCEPTION;
        }
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
            connect();
            Statement statement = connection.createStatement();

            //Check login state
            ResultSet rs = statement.executeQuery("SELECT * FROM Users WHERE name=\"" + user + "\";");
            if(!rs.next()){
                System.out.println("NO USER FOUND");
                connection.close();
                return NO_USER_FOUND;
            }
            if (code==Request.LOGIN){ //User wants to login
                boolean alreadyLogin = ((rs.getString("login")).matches("1"));
                if (alreadyLogin){
                    System.out.println("ALREADY LOGIN");
                    connection.close();
                    return ALREADY_LOGIN;
                }
                else{
                    //Updates login state and timestamp
                    long unixTime = System.currentTimeMillis() / 1000L;
                    int rc = statement.executeUpdate("UPDATE Users SET login=\"1\", timestamp=\"" + unixTime + "\" WHERE name=\""
                            + user + "\" AND password=\"" + password + "\";");
                    connection.close();
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
                int rc = statement.executeUpdate("UPDATE Users SET login=\"0\" WHERE name=\""
                        + user + "\";");
                connection.close();

                System.out.println("LOGOUT");
                return 0;
            }

        } catch (SQLException e) {
            System.out.println("Error getting connection to database");
            e.printStackTrace();
            return DB_EXCEPTION;
        }
    }

    /**
     * Small method to open a database connection if one is not already open, valid or in use
     * @throws SQLException
     */
    private void connect() throws SQLException {


        // create the connection
        connection = DriverManager.getConnection("jdbc:sqlite:database/sd.db");
    }
}
