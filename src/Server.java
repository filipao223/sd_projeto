import java.rmi.*;
import java.util.HashMap;

public interface Server extends Remote {

	public void receive(HashMap h) throws java.rmi.RemoteException;

	public void subscribe(String name,Client client) throws java.rmi.RemoteException;

}