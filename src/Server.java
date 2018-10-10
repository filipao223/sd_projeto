import java.rmi.*;
import java.util.ArrayList;

public interface Server extends Remote {

	public void remote_print(Texto m) throws java.rmi.RemoteException;


}