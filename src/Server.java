import java.rmi.*;

public interface Server extends Remote {

	public void receive(Texto m) throws java.rmi.RemoteException;

	public void subscribe(String name, RMIClient client) throws java.rmi.RemoteException;

	public void print_on_server(String s) throws java.rmi.RemoteException;
}