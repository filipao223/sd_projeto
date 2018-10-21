import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
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


public class RMIServer extends UnicastRemoteObject implements Server {

	public ArrayList<Client> client = new ArrayList<>();
	public Serializer serializer;
	private static List<Integer> serverNumbers = new ArrayList<>();

	public RMIServer() throws RemoteException {
		super();
	}

	public static void remake(RMIServer server) throws RemoteException, InterruptedException {
		int vezes = 0;
		while (true) {
			Thread.sleep(1000);
			Registry r = LocateRegistry.getRegistry(1099);
			try {
				System.out.println("À procura");
				r.lookup("MainServer");
			}catch (ExportException e){
				System.out.println("Já existe um");
			} catch (NotBoundException e) {
				System.out.println("Nenhum server");
				vezes += 1;
				if(vezes == 5){
					r.rebind("MainServer", server);
					System.out.println("Server ready.");
					vezes = 0;
					break;
				}
			} catch (ConnectException e) {
				System.out.println("MainServer");
				vezes += 1;
				if(vezes == 5){
					r.rebind("MainServer", server );
					System.out.println("Server ready.");
					vezes = 0;
					break;
				}
			}
		}
	}

	public void subscribe(String name,Client c) throws RemoteException {
		client.add(c);
		System.out.println(c);
	}


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

		try {
			socket = new MulticastSocket();  // create socket without binding it (only for sending)
			Scanner keyboardScanner = new Scanner(System.in);

			//Pedido inicial, coloca a feature CHECK_SERVER_UP, a qual o servidor vai responder
			//(ou nao) o seu numero
			Map<String, Object> checkServer = new HashMap<>();
			checkServer.put("feature", String.valueOf(Request.CHECK_SERVER_UP));

			byte[] buffer = s.serialize(checkServer);

			InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
			socket.send(packet); //Envia

			buffer = s.serialize(h);

			group = InetAddress.getByName(MULTICAST_ADDRESS);
			packet = new DatagramPacket(buffer, buffer.length, group, PORT);
			socket.send(packet);



		} catch (IOException e) {
			e.printStackTrace();
		}

	}


	// =======================================================

	public static void main(String args[]) {


		try {
			RMIServer s_main = new RMIServer();
			RMIServer s_backup = new RMIServer();
			Registry r = LocateRegistry.createRegistry(1099);
			r.rebind("MainServer", s_main);
			System.out.println("Server ready.");
			while(true){
				s_backup.remake(s_backup);
				s_main.remake(s_main);
			}
		}catch (RemoteException re) {
			System.out.println("Exception in RMIServer.main: " + re);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}