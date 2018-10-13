import java.io.Serializable;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.util.*;

public class RMIClient implements Serializable {

    public int valor = 5;

    public void print_on_client() throws RemoteException {

        /*System.out.println("Estou no cliente");
        Set set = h.entrySet();
		Iterator iterator = set.iterator();
		while(iterator.hasNext()) {
			Map.Entry mentry = (Map.Entry)iterator.next();
			System.out.print("key is: "+ mentry.getKey() + " & Value is: ");
			System.out.println(mentry.getValue());
		}*/
        System.out.println("Hey");
    }

    public static void main(String[] args){
        //Codes example


        try {

            Server h = (Server) LocateRegistry.getRegistry(1099).lookup("MainServer");

            String linha = null;

            RMIClient c = new RMIClient();

            //h.print_on_server("Ola");

            do{
                int code = 1;

                switch(code){
                    case Request.LOGIN:
                        System.out.println("Faça o Login");
                        Scanner keyboard = new Scanner(System.in);
                        linha = keyboard.nextLine();
                        String[] partes = linha.split("_");
                        ArrayList<String> A = new ArrayList<>();


                        String insere = null;
                        String s = null;

                        for(int i=0;i<2;i++){
                            if (i == 0){
                                s = "username_";
                                insere = s.concat(partes[i]);
                                A.add(insere);
                            }
                            else if(i == 1){
                                s = "acao_";
                                insere = s.concat(partes[i]);
                                A.add(insere);
                            }
                        }

                        s = "feature_";
                        insere = s.concat(String.valueOf(code));

                        Texto t = new Texto(A,insere);

                        h.subscribe(partes[0],c);

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
