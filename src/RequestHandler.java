
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
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

    private int NO_LOGIN        = 1;
    private int NO_USER_FOUND   = 2;
    private int ALREADY_LOGIN   = 5;
    private int ALREADY_EDITOR  = 3;

    RequestHandler(DatagramPacket packet){
        this.clientPacket = packet;
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
                            else if (rc==-1) sendCallback(user, "Wrong user/password", null);
                            else sendCallback(user, "User logged in", null);
                        }
                        else{
                            if (rc==NO_USER_FOUND) sendCallback(user, "User not found", null);
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
                        newEditor = (String) data.get("user");

                        if(editor==null || newEditor==null){
                            sendCallback(editor, "Bad data", null);
                            break;
                        }

                        int rc = makeEditorHandler(editor, newEditor);
                        if (rc == NO_LOGIN) sendCallback(editor, "User not logged in", null);
                        else if (rc == NO_USER_FOUND) sendCallback(editor, "User not found", null);
                        else if (rc == ALREADY_EDITOR) sendCallback(editor, "User already is editor", null);
                        else sendCallback(editor, "\"" + newEditor + "\" is now editor", null);

                    } catch (ParseException | IOException e) {
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
    private int makeEditorHandler(String editor, String newEditor) throws IOException, ParseException {
        editor = (String) data.get("editor");
        newEditor = (String) data.get("user");
        String message = "User \"" + editor + "\" wants to make \"" + newEditor + "\" an editor";
        System.out.println(message);

        JSONParser parser = new JSONParser();
        String path="JSON" + File.separator +"user.json";
        Object obj = parser.parse(new FileReader(path));
        //System.out.println(obj);

        JSONArray jsonData = (JSONArray) obj;
        //System.out.println(jsonData);

        boolean gotEditor = false;
        boolean gotUser = false;

        JSONObject newEditorJson = null;

        for(Object item:jsonData.subList(0, jsonData.size())){
            JSONObject userJson = (JSONObject) item;
            String gotName  = (String)userJson.get("name");

            if(gotEditor && gotUser){
                if((boolean)userJson.get("editor")) return ALREADY_EDITOR;
                newEditorJson.put("editor", true);
            }

            if(gotName.matches(editor)){
                if(!(boolean)userJson.get("login")) return NO_LOGIN;
                gotEditor = true;
            }
            else if(gotName.matches(newEditor)){
                newEditorJson = userJson;
                gotUser = true;
            }
        }

        return NO_USER_FOUND;
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

    @SuppressWarnings("unchecked")
    private int loginHandler(int code){
        Connection connection = null;

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
        }

        return NO_USER_FOUND;
    }
}
