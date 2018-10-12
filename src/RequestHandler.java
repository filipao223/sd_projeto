
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

public class RequestHandler implements Runnable {
    private String MULTICAST_ADDRESS = "224.3.2.1";
    private int PORT = 4321;

    private Map<String, Object> data = null;

    RequestHandler(Map<String, Object> data){
        this.data = data;
    }

    @SuppressWarnings("unchecked")
    public void run(){
        if (data != null){
            //Check which feature user wants to do
            int code = Integer.parseInt((String)data.get("feature"));
            switch(code){

                case Request.LOGIN:
                    try{
                        //Get username
                        String user = (String) data.get("username");
                        String message = "User \"" + user + "\" wants to login";
                        System.out.println(message);
                        String password = (String) data.get("password");

                        JSONParser parser = new JSONParser();
                        String path="JSON" + File.separator +"user.json";
                        Object obj = parser.parse(new FileReader(path));
                        //System.out.println(obj);

                        JSONArray jsonData = (JSONArray) obj;
                        //JSONObject correctUser = null;
                        //System.out.println(jsonData);

                        for(Object item:jsonData.subList(0, jsonData.size())){
                            JSONObject userJson = (JSONObject) item;
                            String gotName  = (String)userJson.get("name");
                            String gotPass = (String)userJson.get("password");

                            if (  gotName.matches(user)  && gotPass.matches(password)){
                                System.out.println("USER \"" + user + "\" LOGGED ON");
                                break;
                            }
                            else{
                                System.out.println("NO LOGIN");
                            }
                        }

                    } catch (ParseException e) {
                        e.printStackTrace();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
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
}
