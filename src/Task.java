public class Task implements Runnable {

    String message = "null";

    Task(String message){
        this.message = message;
    }

    public void run(){
        System.out.println("Received: " + message);
    }
}
