import java.rmi.*;
import java.util.HashMap;

public interface Client extends Remote{

	public void print_on_client(HashMap h) throws java.rmi.RemoteException;

}