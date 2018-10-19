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


	public byte[] serialize(Object obj) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectOutputStream os = new ObjectOutputStream(out);
		os.writeObject(obj);
		return out.toByteArray();
	}

	public void subscribe(String name,Client c) throws RemoteException {
		client.add(c);
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
			RMIServer s = new RMIServer();
			Registry r = LocateRegistry.createRegistry(1099);
			r.rebind("MainServer", s);
			System.out.println("Server ready.");
		}catch (RemoteException re) {
			System.out.println("Exception in RMIServer.main: " + re);
		}

	}

}