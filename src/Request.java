public class Request {
    //Features
    protected static final int LOGIN             = 1;  //fazer login
    protected static final int MANAGE            = 2;  //gerir artistas, musicas e albuns
    protected static final int SEARCH            = 3;  //procurar musicas por album ou artista
    protected static final int DETAILS           = 4;  //consultar detalhes sobre album e artista
    protected static final int CRITIQUE          = 5;  //escrever critica a album
    protected static final int MAKE_EDITOR       = 6;  //dar privilegios de editor a user
    protected static final int NOTE_EDITOR       = 7;  //notificação de novo editor
    protected static final int NOTE_NEW_EDIT     = 8;  //notificaçao de novo edit
    protected static final int NOTE_DELIVER      = 9;  //entregar notificação a user previ. off
    protected static final int UPLOAD            = 10; //fazer upload de uma musica para o server
    protected static final int SHARE             = 11; //Share de uma musica com users
    protected static final int DOWNLOAD          = 12; //Download de musicas do servidor
    protected static final int CALLBACK          = 13; //Packet returned after processing in server
    protected static final int LOGOUT            = 14; //Logout

    //Edit
    protected static final int EDIT_ALBUM        = 15; //Editar info de albums
    protected static final int EDIT_MUSIC        = 16; //Editar info de musicas
    protected static final int EDIT_ARTIST       = 17; //Editar info de artistas
    protected static final int EDIT_NAME         = 18; //Nome do item
    protected static final int EDIT_YEAR         = 19; //Para items que tenham ano
    protected static final int EDIT_FIELD_ARTIST = 20; //Para items que tenham o campo "artist"
    protected static final int EDIT_DESCRIPTION  = 21; //Descrição do item
    protected static final int EDIT_GENRE        = 22; //Para items que tenham o campo "genre"
    protected static final int EDIT_LYRICS       = 23; //Letras de musicas
    protected static final int EDIT_BIRTH        = 24; //Data de nascimento de um artista
    protected static final int EDIT_FIELD_ALBUMS = 25; //Lista de albums de um artista

    //Pesquisa
    protected static final int SEARCH_ALBUM      = 26; //Pesquisa relacionada com albums
    protected static final int SEARCH_MUSIC      = 27; //Pesquisa relacionada com musicas
    protected static final int SEARCH_ARTIST     = 28; //Pesquisa relacionada com artistas
}
