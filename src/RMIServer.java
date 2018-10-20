import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.net.MulticastSocket;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.*;
import java.util.*;
import java.io.IOException;


public class RMIServer extends UnicastRemoteObject implements Server {

	public ArrayList<Client> client = new ArrayList<>();
	public Serializer serializer;

	public int maxserver = 3;

	public RMIServer() throws RemoteException {
		super();
	}

	public static void remake() throws RemoteException, InterruptedException {
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
					RMIServer s = new RMIServer();
					r = LocateRegistry.createRegistry(1099);
					r.rebind("MainServer", s);
					System.out.println("Server ready.");
					vezes = 0;
					break;
				}
			} catch (ConnectException e) {
				System.out.println("Nenhum server");
				vezes += 1;
				if(vezes == 5){
					RMIServer s = new RMIServer();
					r = LocateRegistry.createRegistry(1099);
					r.rebind("MainServer", s);
					System.out.println("Server ready.");
					vezes = 0;
					break;
				}
			}
		}
	}


	public byte[] serialize(Object obj) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectOutputStream os = new ObjectOutputStream(out);
		os.writeObject(obj);
		return out.toByteArray();
	}

	public void subscribe(String name,Client c) throws RemoteException {
		client.add(c);
		System.out.println(c);
	}


	public void receive(Texto m) throws RemoteException {

		String MULTICAST_ADDRESS = "224.3.2.1";
		int PORT = 4321;

		HashMap<String, Object> hmap = new HashMap<String, Object>();

		String[] partes = m.getCaso().split("_");

		hmap.put(partes[0],partes[1]);
		for(String s : m.getText()){
			partes = s.split("_");
			for(int i = 0;i<partes.length-1;i++){
				hmap.put(partes[i],partes[i+1]);
			}
		}

		hmap.put("server",Integer.toString(maxserver));

		for(Client c:client){
			c.print_on_client(hmap);
		}

		Set set = hmap.entrySet();
		Iterator iterator = set.iterator();
		while(iterator.hasNext()) {
			Map.Entry mentry = (Map.Entry)iterator.next();
			System.out.print("key is: "+ mentry.getKey() + " & Value is: ");
			System.out.println(mentry.getValue());
		}

		MulticastSocket socket= null;

		try{

			socket = new MulticastSocket();  // create socket without binding it (only for sending)
			byte[] buffer = serialize(hmap);

			//System.out.println(buffer.toString());

			//InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
			//DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
			//socket.send(packet);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			socket.close();
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
			s_backup.remake();
		}catch (RemoteException re) {
			System.out.println("Exception in RMIServer.main: " + re);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

}