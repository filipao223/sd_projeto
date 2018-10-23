import java.io.*;
import java.util.ArrayList;

public class Texto implements Serializable {
	public ArrayList<String> text = new ArrayList<>();
	public String caso;

	public Texto(ArrayList<String> text,String caso) {
		this.caso = caso;
		this.text = text;
	}

	public void Print() {
		for (int i = 0; i < text.size(); i++) {
			System.out.println(text.get(i));
		}
	}

	public ArrayList<String> getText(){
		return this.text;
	}

	public String getCaso(){
		return this.caso;
	}
}