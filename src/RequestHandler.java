
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class RequestHandler implements Runnable {
    private String MULTICAST_ADDRESS = "224.3.2.1";
    private int PORT = 4321;

    private DatagramPacket clientPacket;
    private Map<String, Object> data = null;

    private JSONObject correctUser = null;
    private Serializer s = new Serializer();

    private Connection connection;

    private int NO_LOGIN        = 1;
    private int NO_USER_FOUND   = 2;
    private int ALREADY_LOGIN   = 3;
    private int ALREADY_EDITOR  = 4;
    private int NOT_EDITOR      = 5;
    private int DB_EXCEPTION    = 6;

    RequestHandler(DatagramPacket packet, Connection connection){
        this.clientPacket = packet;
        this.connection = connection;
    }

    @SuppressWarnings("unchecked")
    public void run(){
        try{
            data = (Map<String, Object>)s.deserialize(clientPacket.getData());
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        if (data != null){
            //Check which feature user wants to do
            int code = Integer.parseInt((String)data.get("feature"));
            switch(code){

                case Request.LOGIN:
                case Request.LOGOUT:
                    try{
                        String user = (String)data.get("username");

                        //Check in db
                        int rc = loginHandler(code);

                        if (code == Request.LOGIN){
                            if (rc==ALREADY_LOGIN) sendCallback(user, "User already logged in", null);
                            else if (rc==NO_USER_FOUND) sendCallback(user, "User not found", null);
                            else if (rc==DB_EXCEPTION) sendCallback(user, "Database error", null);
                            else if (rc==-1) sendCallback(user, "Wrong user/password", null);
                            else sendCallback(user, "User logged in", null);
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
                                else if (rc==-1) sendCallback(editor, "Failed making new editor", null);
                                else sendCallback(editor, "Made new editor", null);
                            }
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;

                case Request.DOWNLOAD:
                    //Get username
                    String user = (String) data.get("username");
                    String message = "User \"" + user + "\" wants to download";

            }
        }
    }

    @SuppressWarnings("unchecked")
    private int makeEditorHandler(String editor, String newEditor) {

        try{
            connection = DriverManager.getConnection("jdbc:sqlite:database/sd.db");
            Statement statement = connection.createStatement();

            //Check if user to be made editor exists
            int rc = checkIfUserExists(newEditor);
            if (rc==NO_USER_FOUND) return NO_USER_FOUND;
            else{
                //Update the value
                rc = statement.executeUpdate("UPDATE Users SET editor=\"1\" WHERE name=\""
                        + newEditor + "\";");
                connection.close();

                if (rc==-1){
                    System.out.println("ERROR MAKING EDITOR");
                    return -1;
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

    private int checkIfUserExists(String newEditor) {
        try{
            connection = DriverManager.getConnection("jdbc:sqlite:database/sd.db");
            Statement statement = connection.createStatement();

            ResultSet rs = statement.executeQuery("SELECT * FROM Users WHERE name=\""
                    + newEditor + "\";");
            connection.close();

            if(!rs.next()){
                System.out.println("NEW EDITOR NOT FOUND");
                return NO_USER_FOUND;
            }

            return 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return DB_EXCEPTION;
        }
    }

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

    private int checkLoginState(String user){
        try{
            connection = DriverManager.getConnection("jdbc:sqlite:database/sd.db");
            Statement statement = connection.createStatement();

            ResultSet rs = statement.executeQuery("SELECT login FROM Users WHERE name=\""
                    + user + "\";");

            if(!rs.next()){
                System.out.println("NO USER FOUND");
                connection.close();
                return NO_USER_FOUND;
            }

            if((rs.getString("login")).matches("1")){
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

    private int checkIfEditor(String user){
        try{
            connection = DriverManager.getConnection("jdbc:sqlite:database/sd.db");
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

    @SuppressWarnings("unchecked")
    private int loginHandler(int code){
        connection = null;

        try{
            connection = DriverManager.getConnection("jdbc:sqlite:database/sd.db");
            Statement statement = connection.createStatement();

            //Check login state
            String user = (String) data.get("username");
            String password = (String) data.get("password");

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
                    int rc = statement.executeUpdate("UPDATE Users SET login=\"1\" WHERE name=\""
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
}
