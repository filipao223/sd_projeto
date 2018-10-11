import java.rmi.*;
import java.util.ArrayList;

public interface Server extends Remote {

	public void receive(Texto m) throws java.rmi.RemoteException;


}