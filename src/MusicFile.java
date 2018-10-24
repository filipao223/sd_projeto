import java.io.Serializable;

public class MusicFile implements Serializable {
    byte[] fileContent;

    MusicFile(byte[] fileContent){
        this.fileContent = fileContent;
    }
}
