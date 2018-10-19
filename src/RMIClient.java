import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.util.*;
import java.rmi.server.*;
import java.io.*;

public class RMIClient extends UnicastRemoteObject implements Client {

    static String name = null;

    static int edit = 0; // 0 é para n editor, 1 é para editor


    public RMIClient() throws RemoteException {
        super();
    }

    public void print_on_client(HashMap h) throws RemoteException {


        Set set = h.entrySet();
		Iterator iterator = set.iterator();
		while(iterator.hasNext()) {
            Map.Entry mentry = (Map.Entry) iterator.next();
            System.out.print("key is: " + mentry.getKey() + " & Value is: ");
            System.out.println(mentry.getValue());
        }
    }


    public static void remake() throws RemoteException{
            try{
                Server h = (Server) LocateRegistry.getRegistry(1099).lookup("MainServer");

                RMIClient c = new RMIClient();

                String linha = null;

                do{

                    System.out.println("Escolha a opçao");
                    Scanner keyboard = new Scanner(System.in);
                    int code = keyboard.nextInt();
                    ArrayList<String> A = new ArrayList<>();
                    String insere = null;
                    String s = null;
                    String[] partes = null;
                    Texto t = null;

                    switch(code){
                        case Request.LOGIN:
                            System.out.println("Faça o Login");

                            System.out.println("Insira o nome");

                            keyboard = new Scanner(System.in);
                            linha = keyboard.nextLine();
                            name = linha;
                            s = "username_";
                            insere = s.concat(linha);

                            A.add(insere);

                            System.out.println("Insira a password");

                            keyboard = new Scanner(System.in);
                            linha = keyboard.nextLine();
                            s = "password_";
                            insere = s.concat(linha);

                            A.add(insere);

                            s = "feature_";
                            insere = s.concat(String.valueOf(code));

                            t = new Texto(A,insere);

                            h.subscribe(name,(Client) c);
                            System.out.println(c.edit);

                            h.receive(t);

                            break;

                        case Request.MANAGE:
                            if(name != null){
                                s = "username_";
                                insere = s.concat(name);
                                A.add(insere);
                            }
                            else{
                                System.out.println("Faça Login");
                                break;
                            }

                            System.out.println("Insira o que quer alterar,o que vai ser alterado e como quer alterar, separado por _");
                            keyboard = new Scanner(System.in);
                            linha = keyboard.nextLine();
                            s = "action_";
                            insere = s.concat(linha);

                            A.add(insere);
                            s = "feature_";

                            insere = s.concat(String.valueOf(code));

                            t = new Texto(A,insere);

                            h.receive(t);

                            break;

                        case Request.SEARCH:
                            System.out.println("Insira o que quer pesquisar");
                            keyboard = new Scanner(System.in);
                            linha = keyboard.nextLine();

                            s = "type_";
                            insere = s.concat(linha);
                            A.add(insere);

                            System.out.println("Insira o nome do que quer pesquisar");
                            keyboard = new Scanner(System.in);
                            linha = keyboard.nextLine();

                            s = "name_";
                            insere = s.concat(linha);
                            A.add(insere);

                            s = "feature_";
                            insere = s.concat(String.valueOf(code));
                            t = new Texto(A,insere);

                            h.receive(t);
                            break;

                        case Request.DETAILS:
                            System.out.println("Insira o nome do que quer pesquisar");
                            keyboard = new Scanner(System.in);
                            linha = keyboard.nextLine();

                            s = "name_";
                            insere = s.concat(linha);
                            A.add(insere);

                            s = "feature_";
                            insere = s.concat(String.valueOf(code));
                            t = new Texto(A,insere);

                            h.receive(t);
                            break;

                        case Request.CRITIQUE:
                            System.out.println("Insira quem quer criticar");
                            keyboard = new Scanner(System.in);
                            linha = keyboard.nextLine();
                            s = "user_";
                            insere = s.concat(linha);
                            A.add(insere);

                            System.out.println("Insira o album que quer criticar");
                            keyboard = new Scanner(System.in);
                            linha = keyboard.nextLine();
                            s = "album_";
                            insere = s.concat(linha);
                            A.add(insere);

                            System.out.println("Insira a critica");
                            keyboard = new Scanner(System.in);
                            linha = keyboard.nextLine();
                            s = "critique_";
                            insere = s.concat(linha);
                            A.add(insere);

                            s = "feature_";
                            insere = s.concat(String.valueOf(code));
                            t = new Texto(A,insere);

                            h.receive(t);

                            break;

                        case Request.MAKE_EDITOR:
                            if(name != null){
                                s = "editor_";
                                insere = s.concat(name);
                                A.add(insere);
                            }
                            else{
                                System.out.println("Faça Login");
                                break;
                            }

                            System.out.println("Insira quem quer tornar editor");
                            s = "user_";
                            insere = s.concat(name);
                            A.add(insere);

                            s = "feature_";
                            insere = s.concat(String.valueOf(code));
                            t = new Texto(A,insere);

                            h.receive(t);
                            break;

                        case Request.NOTE_EDITOR:

                        case Request.NOTE_DELIVER:

                        case Request.UPLOAD:

                            System.out.println("Insira quem quer mandar");
                            keyboard = new Scanner(System.in);
                            linha = keyboard.nextLine();
                            s = "user_";
                            insere = s.concat(linha);
                            A.add(insere);

                            System.out.println("Insira a musica que quer");
                            keyboard = new Scanner(System.in);
                            linha = keyboard.nextLine();
                            s = "music_";
                            insere = s.concat(linha);
                            A.add(insere);

                            s = "feature_";
                            insere = s.concat(String.valueOf(code));
                            t = new Texto(A,insere);

                            h.receive(t);

                        case Request.SHARE:

                            System.out.println("Insira para quem quer mandar,separado por _");
                            keyboard = new Scanner(System.in);
                            linha = keyboard.nextLine();
                            s = "user_";
                            insere = s.concat(linha);
                            A.add(insere);

                            System.out.println("Insira a musica que quer");
                            keyboard = new Scanner(System.in);
                            linha = keyboard.nextLine();
                            s = "music_";
                            insere = s.concat(linha);
                            A.add(insere);

                            s = "feature_";
                            insere = s.concat(String.valueOf(code));
                            t = new Texto(A,insere);

                            h.receive(t);
                            break;

                        case Request.DOWNLOAD:
                            //Download something
                            break;
                    }
                }while(!linha.equals("END"));
            }catch(NotBoundException re){
                remake();
            }catch(ConnectException re){
                remake();
            }
    }

    public static void main(String[] args){
        //Codes example
        try {

            Server h = (Server) LocateRegistry.getRegistry(1099).lookup("MainServer");

            RMIClient c = new RMIClient();

            String linha = null;

            do{

                System.out.println("Escolha a opçao");
                Scanner keyboard = new Scanner(System.in);
                int code = keyboard.nextInt();
                ArrayList<String> A = new ArrayList<>();
                String insere = null;
                String s = null;
                String[] partes = null;
                Texto t = null;

                switch(code){
                    case Request.LOGIN:
                        System.out.println("Faça o Login");

                        System.out.println("Insira o nome");

                        keyboard = new Scanner(System.in);
                        linha = keyboard.nextLine();
                        name = linha;
                        s = "username_";
                        insere = s.concat(linha);

                        A.add(insere);

                        System.out.println("Insira a password");

                        keyboard = new Scanner(System.in);
                        linha = keyboard.nextLine();
                        s = "password_";
                        insere = s.concat(linha);

                        A.add(insere);

                        s = "feature_";
                        insere = s.concat(String.valueOf(code));

                        t = new Texto(A,insere);

                        h.subscribe(name,(Client) c);
                        System.out.println(c.edit);

                        h.receive(t);

                        break;

                    case Request.MANAGE:
                        if(name != null){
                            s = "username_";
                            insere = s.concat(name);
                            A.add(insere);
                        }
                        else{
                            System.out.println("Faça Login");
                            break;
                        }

                        System.out.println("Insira o que quer alterar,o que vai ser alterado e como quer alterar, separado por _");
                        keyboard = new Scanner(System.in);
                        linha = keyboard.nextLine();
                        s = "action_";
                        insere = s.concat(linha);

                        A.add(insere);
                        s = "feature_";

                        insere = s.concat(String.valueOf(code));

                        t = new Texto(A,insere);

                        h.receive(t);

                        break;

                    case Request.SEARCH:
                        System.out.println("Insira o que quer pesquisar");
                        keyboard = new Scanner(System.in);
                        linha = keyboard.nextLine();

                        s = "type_";
                        insere = s.concat(linha);
                        A.add(insere);

                        System.out.println("Insira o nome do que quer pesquisar");
                        keyboard = new Scanner(System.in);
                        linha = keyboard.nextLine();

                        s = "name_";
                        insere = s.concat(linha);
                        A.add(insere);

                        s = "feature_";
                        insere = s.concat(String.valueOf(code));
                        t = new Texto(A,insere);

                        h.receive(t);
                        break;

                    case Request.DETAILS:
                        System.out.println("Insira o nome do que quer pesquisar");
                        keyboard = new Scanner(System.in);
                        linha = keyboard.nextLine();

                        s = "name_";
                        insere = s.concat(linha);
                        A.add(insere);

                        s = "feature_";
                        insere = s.concat(String.valueOf(code));
                        t = new Texto(A,insere);

                        h.receive(t);
                        break;

                    case Request.CRITIQUE:
                        System.out.println("Insira quem quer criticar");
                        keyboard = new Scanner(System.in);
                        linha = keyboard.nextLine();
                        s = "user_";
                        insere = s.concat(linha);
                        A.add(insere);

                        System.out.println("Insira o album que quer criticar");
                        keyboard = new Scanner(System.in);
                        linha = keyboard.nextLine();
                        s = "album_";
                        insere = s.concat(linha);
                        A.add(insere);

                        System.out.println("Insira a critica");
                        keyboard = new Scanner(System.in);
                        linha = keyboard.nextLine();
                        s = "critique_";
                        insere = s.concat(linha);
                        A.add(insere);

                        s = "feature_";
                        insere = s.concat(String.valueOf(code));
                        t = new Texto(A,insere);

                        h.receive(t);

                        break;

                    case Request.MAKE_EDITOR:
                        if(name != null){
                            s = "editor_";
                            insere = s.concat(name);
                            A.add(insere);
                        }
                        else{
                            System.out.println("Faça Login");
                            break;
                        }

                        System.out.println("Insira quem quer tornar editor");
                        s = "user_";
                        insere = s.concat(name);
                        A.add(insere);

                        s = "feature_";
                        insere = s.concat(String.valueOf(code));
                        t = new Texto(A,insere);

                        h.receive(t);
                        break;

                    case Request.NOTE_EDITOR:

                    case Request.NOTE_DELIVER:

                    case Request.UPLOAD:

                        System.out.println("Insira quem quer mandar");
                        keyboard = new Scanner(System.in);
                        linha = keyboard.nextLine();
                        s = "user_";
                        insere = s.concat(linha);
                        A.add(insere);

                        System.out.println("Insira a musica que quer");
                        keyboard = new Scanner(System.in);
                        linha = keyboard.nextLine();
                        s = "music_";
                        insere = s.concat(linha);
                        A.add(insere);

                        s = "feature_";
                        insere = s.concat(String.valueOf(code));
                        t = new Texto(A,insere);

                        h.receive(t);

                    case Request.SHARE:

                        System.out.println("Insira para quem quer mandar,separado por _");
                        keyboard = new Scanner(System.in);
                        linha = keyboard.nextLine();
                        s = "user_";
                        insere = s.concat(linha);
                        A.add(insere);

                        System.out.println("Insira a musica que quer");
                        keyboard = new Scanner(System.in);
                        linha = keyboard.nextLine();
                        s = "music_";
                        insere = s.concat(linha);
                        A.add(insere);

                        s = "feature_";
                        insere = s.concat(String.valueOf(code));
                        t = new Texto(A,insere);

                        h.receive(t);
                        break;

                    case Request.DOWNLOAD:
                        //Download something
                        break;
                }
            }while(!linha.equals("END"));
        } catch (ConnectException e){
            try {
                remake();
            } catch (RemoteException e1) {
                e1.printStackTrace();
            }
        } catch (Exception e) {
            System.out.println("Exception in main: " + e);
            e.printStackTrace();
        }
    }

}
