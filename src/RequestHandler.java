
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import com.google.gson.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
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
    private int ALREADY_EDITOR  = 3;
    private int WRONG_PASSWORD  = 4;

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
                            if (rc==NO_USER_FOUND) sendCallback(user, "User not found", null);
                            else if (rc==WRONG_PASSWORD) sendCallback(user, "Wrong password", null);
                            else sendCallback(user, "User logged in", null);
                        }
                        else{
                            if (rc==NO_USER_FOUND) sendCallback(user, "User not found", null);
                            else sendCallback(user, "User logged out", null);
                        }



                    } catch (ParseException | IOException e) {
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
    private int loginHandler(int code) throws IOException, ParseException {
        //Get username
        String user = (String) data.get("username");
        String message = "User \"" + user + "\" wants to login";
        System.out.println(message);
        String password = (String) data.get("password");

        String path="JSON" + File.separator +"user.json";
        JsonParser parser = new JsonParser();
        JsonElement jsonElement = parser.parse(new FileReader(path));
        JsonArray jsonData = jsonElement.getAsJsonArray();

        //System.out.println(obj);

        //JSONArray jsonData = (JSONArray) obj;
        //System.out.println(jsonData);

        for(int i=0; i<jsonData.size(); i++){
            JsonObject userJson = jsonData.get(i).getAsJsonObject();
            String gotName = userJson.get("name").getAsString();
            String gotPass = userJson.get("password").getAsString();

            if (code == Request.LOGIN){
                if (  gotName.matches(user)  && gotPass.matches(password)){
                    System.out.println("USER \"" + user + "\" LOGGED ON");
                    userJson.remove("login");
                    userJson.addProperty("login", true);

                    System.out.println("user login state is " + userJson.get("login").getAsBoolean());

                    return 0;
                }
                else if(gotName.matches(user)){
                    System.out.println("Wrong password");
                    return WRONG_PASSWORD;
                }
            }
            else{
                if(gotName.matches("user")){
                    System.out.println("Wrong password");
                    return 0;
                }
            }
        }

        System.out.println(code==Request.LOGIN?"NO LOGIN":"NO LOGOUT");
        return NO_USER_FOUND;
    }
}
