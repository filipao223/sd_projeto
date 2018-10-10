import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.util.*;

public class Main {
    public static void main(String[] args){
        //Codes example


        try {

            Server h = (Server) LocateRegistry.getRegistry(1099).lookup("Main");

            String linha = null;
            do{
                int code = 1;

                switch(code){
                    case Request.LOGIN:
                        System.out.println("Faça o Login");
                        Scanner keyboard = new Scanner(System.in);
                        linha = keyboard.nextLine();
                        String[] partes = linha.split("/");
                        ArrayList<String> A = new ArrayList<>();

                        for (String s:partes){
                            A.add(s);
                        }

                        Texto t = new Texto(A,code);

                        h.remote_print(t);

                        break;
                    case Request.DOWNLOAD:
                        //Download something
                        break;
                }
            }while(!linha.equals("END"));


        } catch (Exception e) {
            System.out.println("Exception in main: " + e);
        }

    }

}
