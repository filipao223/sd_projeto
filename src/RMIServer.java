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

	static RMIClient client;
	public Serializer serializer;

	public RMIServer() throws RemoteException {
		super();
	}

	public void print_on_server(String s) throws RemoteException {
		System.out.println("> " + s);
	}

	public byte[] serialize(Object obj) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectOutputStream os = new ObjectOutputStream(out);
		os.writeObject(obj);
		return out.toByteArray();
	}

	public void subscribe(String name, RMIClient c) throws RemoteException {
		System.out.println("Subscribing " + name);
		client = c;
		client.print_on_client();
	}


	public void receive(Texto m) throws RemoteException {
		/*for (String s : m.getText()) {
			System.out.println("Server:" + s);
		}

		System.out.println(m.getCaso());*/
		String MULTICAST_ADDRESS = "224.3.2.1";
		int PORT = 4321;

		HashMap<String, Object> hmap = new HashMap<String, Object>();

		String[] partes = m.getCaso().split("_");

		hmap.put(partes[0],partes[1]);
		for(String s : m.getText()){
			partes = s.split("_");
			System.out.println(partes[0]);
			System.out.println(partes[1]);
			for(int i = 0;i<2;i++){
				hmap.put(partes[0],partes[1]);
			}
		}

		//client.print_on_client();




		/*Set set = hmap.entrySet();
		Iterator iterator = set.iterator();
		while(iterator.hasNext()) {
			Map.Entry mentry = (Map.Entry)iterator.next();
			System.out.print("key is: "+ mentry.getKey() + " & Value is: ");
			System.out.println(mentry.getValue());
		}*/

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

		String a;

		try {
			RMIServer h = new RMIServer();
			Registry r = LocateRegistry.createRegistry(1099);
			r.rebind("MainServer", h);
			System.out.println("Server ready.");
		} catch (RemoteException re) {
			System.out.println("Exception in RMIServer.main: " + re);
		}

	}

}