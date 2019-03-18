import java.io.IOException;
import java.net.URISyntaxException;

import javafx.application.Application;
import sam.book.search.App;

public class Main {
	public static void main(String[] args) throws InterruptedException, URISyntaxException, IOException {
		App.startSearchFile = args.length == 0 ? null : args[0];
		Application.launch(App.class, new String[0]);
	}
}
