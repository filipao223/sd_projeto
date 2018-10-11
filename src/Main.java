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

                        String insere = null;
                        String s = null;

                        for(int i=0;i<2;i++){
                            if (i == 0){
                                s = "username/";
                                insere = s.concat(partes[i]);
                                A.add(insere);
                            }
                            else if(i == 1){
                                s = "acao/";
                                insere = s.concat(partes[i]);
                                A.add(insere);
                            }
                        }

                        s = "feature/";
                        insere = s.concat(String.valueOf(code));

                        Texto t = new Texto(A,insere);

                        h.receive(t);

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
