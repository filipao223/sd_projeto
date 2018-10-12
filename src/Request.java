public class Request {
    protected static final int LOGIN               = 1;  //fazer login
    protected static final int MANAGE            = 2;  //gerir artistas, musicas e albuns
    protected static final int SEARCH              = 3;  //procurar musicas por album ou artista
    protected static final int DETAILS             = 4;    //consultar detalhes sobre album e artista
    protected static final int CRITIQUE           = 5;    //escrever critica a album
    protected static final int MAKE_EDITOR    = 6;    //dar privilegios de editor a user
    protected static final int NOTE_EDITOR    = 7;    //notificação de novo editor
    protected static final int NOTE_NEW_EDIT= 8;    //notificaçao de novo edit
    protected static final int NOTE_DELIVER   = 9;   //entregar notificação a user previ. off
    protected static final int UPLOAD            = 10; //fazer upload de uma musica para o server
    protected static final int SHARE               = 11;  //Share de uma musica com users
    protected static final int DOWNLOAD     = 12;  //Download de musicas do servidor
    protected static final int CALLBACK     = 13;  //Packet returned after processing in server

     final int levelCode;

     Request(int levelCode) {
        this.levelCode = levelCode;
    }

    public int getCode(){
        return this.levelCode;
    }
}
