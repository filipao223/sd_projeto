public class Main {
    public static void main(String[] args){
        //Codes example

        //Received code from udp datagram (1)
        int code = 1;

        switch(code){
            case Request.LOGIN:
                //Do login
                break;
            case Request.DOWNLOAD:
                //Download something
                break;
        }
    }
}
