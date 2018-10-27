import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.*;
import java.util.*;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Class that will receive all the hashmaps, save the clients(using an ArrayList), start the servers,
 * and send UDP datagram
 * @author Joao Mendes
 */
public class RMIServer extends UnicastRemoteObject implements Server {

	public ArrayList<Client> client = new ArrayList<>(); //contem todos os clientes que vão estar ligados ao server
	public static Serializer serializer = new Serializer();
	private static List<Integer> serverNumbers = new ArrayList<>();
	private static String MULTICAST_ADDRESS = "226.0.0.1";
	private static int PORT = 4321;
	private static ExecutorService executor = Executors.newFixedThreadPool(5);

    /**
     * Constructor of RMIServer
     * @throws RemoteException
     * @author Joao Mendes
     */
	public RMIServer() throws RemoteException {
		super();
	}

	/**
	 * This function is the one the one responsible for the pokes that the secondary servers sends to the primary
	 * It enters the while cicle, always using lookup to check the state of the main Server,if its not bound, or
	 * it cant connect 5 times, it uses the rebind to promote de secondary to primary
	 * @param backup The secondary server
	 * @param main The primary server
	 * @throws RemoteException
	 * @throws InterruptedException
     * @author Joao Mendes
	 */
	public static void remake(RMIServer backup,RMIServer main) throws RemoteException, InterruptedException {
		int vezes = 0;
		while (true) {
			Thread.sleep(5000);
			Registry r = LocateRegistry.getRegistry(1099); // busca o registo do port 1099
			try {
				r.lookup("MainServer"); // verifica se o port já contem o MainServer
			}catch (ExportException e){
			} catch (NotBoundException e) { // se não contem testa 5 vezes e depois o servidor secundario assume o port
				vezes += 1;
				if(vezes == 5){
					r.rebind("MainServer", backup);
					backup.client = main.client;
					System.out.println("Server ready.");
					vezes = 0;
					break;
				}
			} catch (ConnectException e) { // se não contem testa 5 vezes e depois o servidor secundario assume o port
				System.out.println("MainServer");
				vezes += 1;
				if(vezes == 5){
					r.rebind("MainServer", backup);
					backup.client = main.client;
					System.out.println("Server ready.");
					vezes = 0;
					break;
				}
			}
		}
	}

	/**
	 * This functions add the Client to the ArrayList on the Server if it isnt there already
	 * @param name Name of the Client
	 * @param c ID of the Client
	 * @throws RemoteException
     * @author Joao Mendes
	 */
	public void subscribe(String name,Client c) throws RemoteException {
		System.out.println(c);
		if(!client.contains(c)) { //verifica se o arraylist de clients contem o cliente, se não, adiciona
			client.add(c);
			System.out.println("Subscribe " + name);
		}
	}


	/**
	 * This functions receives a HashMap
	 * @param h HashMap
	 * @throws RemoteException
     * @author Joao Mendes
	 */
	public void receive(HashMap h) throws RemoteException {

		String MULTICAST_ADDRESS = "226.0.0.1";
		int PORT = 4321;
		Serializer s = new Serializer();
		MulticastSocket socket = null;

		Set set = h.entrySet();
		Iterator iterator = set.iterator();
		while(iterator.hasNext()) {
			Map.Entry mentry = (Map.Entry) iterator.next();
			System.out.print("key is: " + mentry.getKey() + " & Value is: ");
			System.out.println(mentry.getValue());
		}

		//Send data to worker thread
        executor.submit(new SendPacket(serverNumbers, h));

	}


	// =======================================================

	public static void main(String args[]) {


		try {
			RMIServer s_main = new RMIServer(); //cria servidor principal
			RMIServer s_backup = new RMIServer(); //cria servidor de backup
			Registry r = LocateRegistry.createRegistry(1099); //cria um registo para se conectar
			r.rebind("MainServer", s_main); //anexa o servidor principal ao registo criado
			System.out.println("Server ready.");

			ReceivePacket receivePacket = new ReceivePacket(serverNumbers,s_main.client);
			receivePacket.start();

            try{
                //First check if there are available servers
                //Keep trying to connect
                MulticastSocket socket = new MulticastSocket();  // create socket without binding it (only for sending)
                Scanner keyboardScanner = new Scanner(System.in);

                //Pedido inicial, coloca a feature CHECK_SERVER_UP, a qual o servidor vai responder
                //(ou nao) o seu numero
                Map<String, Object> checkServer = new HashMap<>();
                checkServer.put("feature", String.valueOf(Request.CHECK_SERVER_UP));

                byte[] buffer = serializer.serialize(checkServer);

                InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                socket.send(packet); //Envia

            } catch (java.net.UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

			while(true){ //num loop, o backup server verifica o principal, se o backup assumir o registo, o principal verifica o backup
				s_backup.remake(s_backup,s_main);
				s_main.remake(s_main,s_backup);
			}
        }catch (RemoteException re) {
			System.out.println("Exception in RMIServer.main: " + re);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}

/**
 * Class that will receive the packet from the server, and, hand it to a worker thread that will
 * eventually send it to the MultiCast Server
 * @author Joao Mendes
 */
class ReceivePacket extends Thread{
    private static String MULTICAST_ADDRESS = "226.0.0.1";
    private static int PORT = 4321;
    private static Serializer serializer = new Serializer();
    private static List<Integer> serverNumbers;
    private static ArrayList<Client> clients;

    /**
     * Constructor of ReceivePacket
     * @param serverNumbers The integer List of all available server numbers.
     * @param clients The list of connected RMI clients.
     * @author Joao Mendes
     */
    ReceivePacket(List<Integer> serverNumbers,ArrayList<Client> clients){
        super("RMIServer");
        ReceivePacket.serverNumbers = serverNumbers;
        this.clients = clients;
    }

    @Override
    public void run() {
        try{
            MulticastSocket socket = new MulticastSocket(PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);
            ExecutorService executor = Executors.newFixedThreadPool(10);

            while (true){
                byte[] buffer = new byte[8192];
                DatagramPacket packetIn = new DatagramPacket(buffer, buffer.length);
                socket.receive(packetIn);

                //Hand off to worker
                executor.submit(new Worker(serverNumbers, packetIn,clients));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	/**
	 * Class that receive the packet sent by the MultiCast Server and sends it to the correct client
     * @author Joao Mendes
	 */
	class Worker implements Runnable{
        private List<Integer> serverNumbers;
        private DatagramPacket packetIn;
        private ArrayList<Client> clients;

        /**
         * Constructor of Worker
         * @param serverNumbers The integer List of all available server numbers.
         * @param packetIn The packet received over UDP multicast from the multicast server.
         * @param clients The list of connected RMI clients.
         * @author Joao Mendes
         */
        Worker(List<Integer> serverNumbers, DatagramPacket packetIn, ArrayList<Client> clients){
            this.serverNumbers = serverNumbers;
            this.packetIn = packetIn;
            this.clients = clients;
        }

        @Override
        public void run() {
			try{
				Map<String, Object> data = (Map<String, Object>) serializer.deserialize(packetIn.getData());


				/*if (data.get("feature_requested") != null) {
					if (data.get("feature_requested").equals(7)) {
						for (Client c : clients) {
							if (c.getName().matches((String) data.get("username"))) {
								c.print_on_client(data);
							}
						}
					}
				}*/
				for(Client c : clients){
					if( c.getName().matches((String) data.get("username"))){
						c.print_on_client(data);
					}
				}
//=============================================RESPOSTAS INTERNAS=============================================================
				//O utilizador não recebe estas mensagens
				//Novo servidor ligado
				if(((String)data.get("feature")).matches("30")){
					System.out.println("-----------New server (" + data.get("new_server") + ")------------");
					serverNumbers.add((int)data.get("new_server")); //Adiciona o numero do servidor à lista da classe
				}
				//Um servidor foi desligado
				else if(((String)data.get("feature")).matches("31")){
					System.out.println("-----------Server down (" + data.get("server_down") + ")------------");
					if (!serverNumbers.isEmpty()) serverNumbers.remove((int)data.get("server_down")); //Remove o numero do servidor da lista da classe
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (Exception e){
				e.printStackTrace();
			}
        }
    }
}

/**
 * The class that is responsible for sending the packet to the MultiCast Server
 * <p>
 * It adds the index number of the server to the HashMap and then sends it to the MultiCast Server
 * @author Joao Mendes
 */
class SendPacket implements Runnable{
	private List<Integer> serverNumbers;
	private static String MULTICAST_ADDRESS = "226.0.0.1";
	private static int PORT = 4321;
	private Map<String, Object> data;
	private int retry = 5000;
	private static Serializer serializer = new Serializer();

    /**
     * Constructor of SendPacket
     * @param serverNumbers The integer List of all available server numbers.
     * @param data The map that is to be sent over an UDP datagram
     * @author Joao Mendes
     */
	SendPacket(List<Integer> serverNumbers, HashMap<String, Object> data){
	    this.serverNumbers = serverNumbers;
	    this.data = data;
    }

    @Override
    public void run() {
        //First generate a server number, if there are any
        //If no servers are available, it keeps trying until there are
        // TODO implement timeout
        try{
            Random r = new Random();
            while (serverNumbers.isEmpty()){
                System.out.println("No servers available, retrying in " + retry/1000 + " seconds");
                Thread.sleep(retry);
            }

            //Servers are available
            int index = r.nextInt(serverNumbers.size());
            //Put server number in hashmap
            data.put("server", String.valueOf(serverNumbers.get(index)));
            //Send the data
            MulticastSocket socket = new MulticastSocket(PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);

            byte[] buffer = serializer.serialize(data);

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
            socket.send(packet);

            //Start packet listener

        } catch (InterruptedException e) {
            System.out.println("Aborted request send");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}