import java.sql.Connection;
import java.sql.DriverManager;

public class Starter {
    Starter(Connection connection){
        MulticastServer server1 = new MulticastServer(connection, 1);
        MulticastServer server2 = new MulticastServer(connection, 2);

        server1.start(); System.out.println("Started MulticastServer1");
        server2.start(); System.out.println("Started MulticastServer2");

        try {
            server1.join();
            server2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args){
        Connection mainDatabaseConnection = null;
        new Starter(mainDatabaseConnection);
    }
}
