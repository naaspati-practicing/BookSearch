package sam.book.search;

import static sam.book.search.ResourceHelper.RESOURCE_DIR;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import sam.book.Book;
import sam.book.BooksHelper;
import sam.book.SmallBook;
import sam.books.BooksDB;
import sam.console.ANSI;
import sam.fx.alert.FxAlert;
import sam.fx.helpers.FxFxml;
import sam.fx.popup.FxPopupShop;
import sam.myutils.Checker;
import sam.myutils.MyUtilsPath;
import sam.reference.WeakAndLazy;

public abstract class FullView extends VBox {
	private static final Logger logger = LoggerFactory.getLogger(FullView.class);
	
	@FXML private Button copyCombined;
	@FXML private Button copyJson;
	@FXML private Button openFile;
	@FXML private Text idText;
	@FXML private Text nameText;
	@FXML private Text file_nameText;
	@FXML private Text path_idText;
	@FXML private Text statusText;
	@FXML private Hyperlink pathText;
	@FXML private Text authorText;
	@FXML private Text isbnText;
	@FXML private Text page_countText;
	@FXML private Text yearText;
	@FXML private WebView descriptionText;
	@FXML private VBox resourceBox;
	
	Book currentBook;
	
	public FullView() {
		System.out.println("FullView loaded");
		try {
			FxFxml.load(ClassLoader.getSystemResource("fxml/FullView.fxml"), this, this);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		resourceBox.visibleProperty().bind(Bindings.isNotEmpty(resourceBox.getChildren()));
	}
	
	@FXML
	private void addResourceAction(Event e) {
		resourceChoice.get().showAndWait();
	}
	
	void reset(SmallBook n) {
		setVisible(n != null);
		if(n == null) return;

		currentBook = booksHelper().book(n);
		Book b = currentBook;
		Path fullPath = booksHelper().getFullPath(b.book);
		openFile.setUserData(fullPath);

		idText.setText(string(b.book.id));
		nameText.setText(b.book.name);
		file_nameText.setText(b.book.filename);
		path_idText.setText(string(b.book.path_id));
		pathText.setText(MyUtilsPath.subpath(fullPath, BooksDB.ROOT).toString());
		pathText.setUserData(fullPath);
		pathText.setTooltip(new Tooltip(fullPath.toString()));
		pathText.setVisited(false);
		authorText.setText(b.author);
		isbnText.setText(b.isbn);
		page_countText.setText(String.valueOf(b.book.page_count));
		yearText.setText(String.valueOf(b.book.year));
		descriptionText.getEngine().loadContent(Checker.isEmpty(b.description) ? "NO DESCRIPTION" : b.description);
		statusText.setText(b.book.getStatus() == null ? null : b.book.getStatus().toString());

		descriptionText.getEngine().setUserStyleSheetLocation(ClassLoader.getSystemResource("css/description.css").toString());

		List<Path> list = getResources(b);
		List<String> list2;
		try {
			list2 = booksHelper().getResources(b);
		} catch (SQLException e) {
			e.printStackTrace();
			list2 = Collections.emptyList();
		}

		List<Node> cl = resourceBox.getChildren();

		if(Checker.isEmpty(list) && Checker.isEmpty(list2))
			cl.clear();
		else {
			LinkedList<Node> nodes = new LinkedList<>(cl);
			cl.clear();
			list.forEach(c -> cl.add(hl(RESOURCE_DIR.resolve(c), nodes.poll())));
			addResourceLinks(list2);
		}

		if(logger.isDebugEnabled())
			logger.debug("change view: {} ", dirname(b));
	}
	
	protected abstract List<Path> getResources(Book b);
    protected abstract BooksHelper booksHelper();
	protected abstract String dirname(Book b);
	protected abstract Node hl(Object c, Node node) ;
	protected abstract Window stage();
	protected abstract void open(boolean open, Node node);

	private static String string(int value) {
		return String.valueOf(value);
	}
	private void addResourceLinks(List<String> resource) {
		resource.forEach(p -> resourceBox.getChildren().add(hl(p.charAt(0) == '\\' ? RESOURCE_DIR.resolve(p.substring(1)) : p, null)));
	}
	
	WeakAndLazy<Stage> resourceChoice = new WeakAndLazy<>(this::resourceChoiceStage);

	private Stage resourceChoiceStage() {
		Stage s = new Stage(StageStyle.UTILITY);
		s.initOwner(stage());
		s.initModality(Modality.APPLICATION_MODAL);
		s.setTitle("resource type");

		Button url = new Button("URL");
		url.setOnAction(e -> newUrlResource());
		Button file = new Button("FILE");
		file.setOnAction(e -> newFileResource());

		HBox box = new HBox(10, url, file);
		box.setPadding(new Insets(10, 20, 10, 20));
		s.setScene(new Scene(box));
		s.sizeToScene();
		s.setResizable(false);

		return s;
	}
	private File lastFileParent;

	private void newFileResource() {
		FileChooser fc = new FileChooser();
		if(lastFileParent != null)
			fc.setInitialDirectory(lastFileParent);
		fc.setTitle("select resource");

		List<File> files = fc.showOpenMultipleDialog(stage());

		if(Checker.isEmpty(files))
			FxPopupShop.showHidePopup("cancelled", 1500);
		else {
			List<Path> paths = Optional.of(resourceBox.getChildren())
					.filter(e -> !e.isEmpty())
					.map(list -> list.stream().map(Node::getUserData).filter(e -> e instanceof Path).map(e -> (Path)e).map(p -> p.toAbsolutePath().normalize()).collect(Collectors.toList()))
					.orElse(Collections.emptyList());

			List<String> result = files.stream()
					.map(f -> f.toPath().toAbsolutePath().normalize())
					.filter(f -> {
						if(paths.contains(f)) {
							logger.warn("already added: {}", f);
							return false;
						}
						return true;
					})
					.map(f -> f.startsWith(RESOURCE_DIR) ? "\\"+f.subpath(RESOURCE_DIR.getNameCount(), f.getNameCount()) : f.toString())
					.collect(Collectors.toList());

			if(result.isEmpty()) {
				logger.warn(ANSI.yellow("already addded"));
				return ;
			}

			try {
				booksHelper().addResource(currentBook, result);
				addResourceLinks(result);
				result.forEach(s -> logger.info("added resource: {}", s));
			} catch (SQLException e2) {
				FxAlert.showErrorDialog(paths.stream().map(Path::toString).collect(Collectors.joining("\n")), "failed to add resource", e2);
			}
		}
	}

	private void newUrlResource() {
		TextInputDialog dialog = new TextInputDialog();
		dialog.initOwner(stage());
		dialog.setTitle("add resources");
		dialog.setHeaderText("Add Resources");

		dialog.showAndWait().ifPresent(s -> {
			if(Checker.isEmptyTrimmed(s))
				FxPopupShop.showHidePopup("bad input", 1500);
			else {
				if(!s.startsWith("http")) {
					logger.warn("missing protocol in url: {}", s);
					FxPopupShop.showHidePopup("bad value", 1500);
					return;
				}
				try {
					new URL(s);
				} catch (MalformedURLException e) {
					FxAlert.showErrorDialog(s, "bad url", e);
					return;
				}

				if(resourceBox.getChildren().isEmpty() || resourceBox.getChildren().stream().map(Node::getUserData).noneMatch(n -> s.equalsIgnoreCase(n.toString()))) {
					try {
						booksHelper().addResource(currentBook, Collections.singletonList(s));
					} catch (SQLException e) {
						FxAlert.showErrorDialog(s, "failed to add to DB", e);
						return;
					}
					resourceBox.getChildren().add(hl(s, null));
				} else 
					FxPopupShop.showHidePopup("already exists", 1500);
			}
		});
	}
	
	@FXML
	private void copyCombinedAction(ActionEvent e) {
		App.copyToClip(dirname(currentBook));
	}
	@FXML
	private void infoGridClickAction(MouseEvent e) {
		if(e.getClickCount() > 1) {
			Object s = e.getTarget();
			if(s instanceof Text) {
				Text t = (Text) s;
				if(!Checker.isEmptyTrimmed(t.getId()))
					App.copyToClip(t.getText());
			}
		}
	} 
	@FXML
	private void openPathAction(ActionEvent e){
		open(false, (Node)e.getSource());
	}
	@FXML
	private void openFileAction(ActionEvent e) {
		open(true, (Node) e.getSource());
	}

	@FXML
	private void copyJsonAction(ActionEvent e) {
		App.copyToClip(BooksDB.toJson(currentBook.book.id, currentBook.isbn, currentBook.book.name));
	}
}
