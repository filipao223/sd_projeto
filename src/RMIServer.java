import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.*;
import java.util.ArrayList;


public class RMIServer extends UnicastRemoteObject implements Server {

	public RMIServer() throws RemoteException {
		super();
	}


	public void remote_print(Texto m) throws RemoteException {
		for (String s : m.getText()) {
			System.out.println("Server:" + s);
		}
		System.out.println(m.getCaso());
	}


	// =======================================================

	public static void main(String args[]) {

		try {
			RMIServer h = new RMIServer();
			Registry r = LocateRegistry.createRegistry(1099);
			r.rebind("Main", h);
			System.out.println("Server ready.");
		} catch (RemoteException re) {
			System.out.println("Exception in RMIServer.main: " + re);
		}

	}

}