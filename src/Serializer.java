import java.io.*;

/**
 * Main serializer code used in the entire project, to ensure correct deserialization
 * every time
 */
public class Serializer {

    /**
     * Simple serializer that converts given object as parameter to a byte array
     * @param obj
     * @return a byte array
     * @throws IOException
     */
    public byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(out);
        os.writeObject(obj);
        return out.toByteArray();
    }

    /**
     * Simple deserializer that converts given byte array as parameter to an object
     * @param data
     * @return converted object
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream is = new ObjectInputStream(in);
        return is.readObject();
    }
}
