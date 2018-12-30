package sam.book.search;
import static sam.myutils.Checker.isEmpty;
import static sam.string.StringUtils.containsAny;

import static sam.book.search.ResourceHelper.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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
import sam.book.Book;
import sam.book.BooksHelper;
import sam.book.SmallBook;
import sam.books.BookStatus;
import sam.books.BooksDB;
import sam.collection.Iterables;
import sam.console.ANSI;
import sam.fx.alert.FxAlert;
import sam.fx.clipboard.FxClipboard;
import sam.fx.helpers.FxFxml;
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.FileOpener;
import sam.myutils.Checker;
import sam.myutils.MyUtilsPath;
import sam.reference.WeakAndLazy;

public class App extends Application {
	// bookId  -> book

	private BooksHelper booksHelper;
	private RecentManager recentManager;

	@FXML private DirFilter dirFilter;
	@FXML private Text searchLabel;
	@FXML private TextField searchField;
	@FXML private TabPane tabpane;
	@FXML private SmallBookTab allTab;
	@FXML private SmallBookTab recentTab;
	@FXML private Text countText;
	@FXML private Text loadText;
	@FXML private VBox vbox;
	@FXML private HBox buttonsBox;
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
	@FXML private ChoiceBox2<Status2> statusChoice;
	@FXML private ChoiceBox2<Sorter> sortChoice;
	@FXML private SplitPane splitpane;
	@FXML private VBox resourceBox;

	@FXML private SqlFilterMenu sqlFilterMenu ;

	private Comparator<SmallBook> currentComparator = Sorter.YEAR.sorter();

	private final Filters filters = new Filters();
	private Book currentBook;
	private static Stage stage;
	private static App instance;
	private SmallBookTab currentTab;

	public static Stage getStage() {
		return stage;
	}
	public static App getInstance() {
		return instance;
	}

	@Override
	public void start(Stage stage) throws Exception {
		App.stage = stage;
		App.instance = this;
		FxFxml.load(ClassLoader.getSystemResource("fxml/App.fxml"), stage, this);
		reset(null, null);

		FxAlert.setParent(stage);
		FxPopupShop.setParent(stage);

		countText.textProperty().bind(Bindings.size(allTab.getListView().getItems()).asString());
		resourceBox.visibleProperty().bind(Bindings.isNotEmpty(resourceBox.getChildren()));

		Platform.runLater(() -> splitpane.setDividerPositions(0.4, 0.6));
		prepareChoiceBoxes();

		searchField.textProperty().addListener((p, o, n) -> {
			currentTab.getListView().getSelectionModel().clearSelection();
			search0(n);
		});
		stage.show();

		booksHelper = new BooksHelper();
		recentManager = new RecentManager();
		readSmallBooks();
		forEachSmallBookTab(t -> {
			t.setSorter(currentComparator);
			t.setBooksHelper(booksHelper);
		});

		tabpane.getSelectionModel().selectedItemProperty().addListener((p, o, n) -> {
			currentTab = (SmallBookTab)n;
			countText.textProperty().bind(Bindings.size(currentTab.getListView().getItems()).asString());
			filters.setAllData(currentTab.getAllData());
		});

		dirFilter.init(booksHelper, filters);
		sqlFilterMenu.init(booksHelper, filters);
		stage.getScene().getStylesheets().add("css/style.css");

		Platform.runLater(() -> {
			currentTab = (SmallBookTab) tabpane.getSelectionModel().getSelectedItem();
			filters.setAllData(currentTab.getAllData());
			filters.setOnChange(() -> currentTab.filter(filters));
			filters.setStringFilter((String)null);
			currentTab.setSorter(currentComparator);
			currentTab.filter(filters);
		});
	}
	private void forEachSmallBookTab(Consumer<SmallBookTab> action) {
		tabpane.getTabs().forEach(t -> {
			if(t instanceof SmallBookTab)
				action.accept((SmallBookTab) t);
		});
	}
	private void prepareChoiceBoxes() throws IOException {
		statusChoice.init(Status2.values());
		statusChoice.selectedProperty()
		.addListener((p, o, n) -> filters.setChoiceFilter(n));

		sortChoice.init(Sorter.values());
		sortChoice.selectedProperty().addListener((p, o, n) -> {
			if(o == n)
				currentComparator = currentComparator.reversed();
			else
				currentComparator = n.sorter();

			forEachSmallBookTab(t -> t.setSorter(currentComparator));
		});
		currentComparator = sortChoice.getSelected().sorter();
		filters.setChoiceFilter(statusChoice.getSelected());
	}
	@FXML
	private void openFileAction(ActionEvent e) {
		open(true, (Node) e.getSource());
	}
	private void open(boolean open, Node node) {
		if(node.getUserData().getClass() == String.class) {
			getHostServices().showDocument((String)node.getUserData());
			return;
		}

		Path p = (Path) node.getUserData();
		if(p == null || Files.notExists(p))
			FxAlert.showErrorDialog(p, "File not found", null);
		else {
			try {
				if(open) {
					getHostServices().showDocument(p.toUri().toString());
					updateRecent();
				} else
					FileOpener.openFileLocationInExplorer(p.toFile());
			} catch (IOException e1) {
				FxAlert.showErrorDialog(p, "Failed to open", e1);
			}
		}
	}

	private void updateRecent() {
		if(recentManager.add(currentBook)) {
			SmallBook sml = allTab.getAllData().stream().filter(s -> s.id == currentBook.book.id).findFirst().get();
			List<SmallBook> list = recentTab.getAllData();
			list.remove(sml);
			list.add(0, sml);

			if(currentTab == recentTab)
				recentTab.filter(filters);
		}
	}
	private void readSmallBooks() throws Exception {
		List<SmallBook> l = booksHelper.getBooks();

		allTab.setAllData(l);
		recentTab.setAllData(recentManager.get(l));
		// applyFilter();
		loadText.setText("cache loaded");
	}

	@FXML
	private void openPathAction(ActionEvent e){
		open(false, (Node)e.getSource());
	}
	@FXML
	private void infoGridClickAction(MouseEvent e) {
		if(e.getClickCount() > 1) {
			Object s = e.getTarget();
			if(s instanceof Text) {
				Text t = (Text) s;
				if(!Checker.isEmptyTrimmed(t.getId()))
					copyToClip(t.getText());
			}
		}
	} 

	@FXML
	private void copyJsonAction(ActionEvent e) {
		copyToClip(BooksDB.toJson(currentBook.book.id, currentBook.isbn, currentBook.book.name));
	}

	@Override
	public void stop() throws Exception {
		super.stop();
		if(booksHelper != null)
			booksHelper.close();
		if(recentManager != null) {
			recentManager.save();
			statusChoice.close();
			sortChoice.close();
		}
	}
	
	private void addResourceLinks(List<String> resource) {
		resource.forEach(p -> resourceBox.getChildren().add(hl(p.charAt(0) == '\\' ? RESOURCE_DIR.resolve(p.substring(1)) : p, null)));
	}
	
	WeakAndLazy<Stage> resourceChoice = new WeakAndLazy<>(this::resourceChoiceStage);

	private Stage resourceChoiceStage() {
		Stage s = new Stage(StageStyle.UTILITY);
		s.initOwner(stage);
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
		
		return s;
	}

	private File lastFileParent;

	private void newFileResource() {
		FileChooser fc = new FileChooser();
		if(lastFileParent != null)
			fc.setInitialDirectory(lastFileParent);
		fc.setTitle("select resource");

		List<File> files = fc.showOpenMultipleDialog(stage);

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
							System.out.println("already added: "+f);
							return false;
						}
						return true;
					})
					.map(f -> f.startsWith(RESOURCE_DIR) ? "\\"+f.subpath(RESOURCE_DIR.getNameCount(), f.getNameCount()) : f.toString())
					.collect(Collectors.toList());
			
			if(result.isEmpty()) {
				System.out.println(ANSI.yellow("already addded"));
				return ;
			}

			try {
				booksHelper.addResource(currentBook, result);
				addResourceLinks(result);
				result.forEach(s -> System.out.println("added resource: "+s));
			} catch (SQLException e2) {
				FxAlert.showErrorDialog(paths.stream().map(Path::toString).collect(Collectors.joining("\n")), "failed to add resource", e2);
			}
		}
	}
	
	private void newUrlResource() {
		TextInputDialog dialog = new TextInputDialog();
		dialog.initOwner(stage);
		dialog.setTitle("add resources");
		dialog.setHeaderText("Add Resources");

		dialog.showAndWait().ifPresent(s -> {
			if(Checker.isEmptyTrimmed(s))
				FxPopupShop.showHidePopup("bad input", 1500);
			else {
				if(!s.startsWith("http")) {
					System.out.println("missing protocol in url: "+s);
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
						booksHelper.addResource(currentBook, Collections.singletonList(s));
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
	private void addResourceAction(Event e) {
		resourceChoice.get().showAndWait();
	}
	@FXML
	private void copyCombinedAction(ActionEvent e) {
		copyToClip(BooksDB.createDirname(currentBook.book.id, currentBook.book.filename));
	}
	private void copyToClip(String s) {
		FxClipboard.setString(s);
		FxPopupShop.showHidePopup("copied: "+s, 1500);
	}
	public void remove(SmallBookTab tab) {
		List<SmallBook> books = tab.getListView().getSelectionModel().getSelectedItems();
		if(books.isEmpty())
			return;
		books = new ArrayList<>(books);
		tab.getListView().getSelectionModel().clearSelection();
		tab.getListView().getItems().removeAll(books);
		if(tab.getType() == TabType.RECENT)
			recentManager.remove(books);
	}

	public void reloadResoueces(Event e) {
		((MenuItem )e.getSource()).setDisable(true);
		ResourceHelper.reloadResoueces();
	}
	public void reset(SmallBook n, TabType type) {
		vbox.setVisible(n != null);
		if(n == null) return;

		System.out.println("change view: "+n.id);

		currentBook = booksHelper.book(n);
		Book b = currentBook;
		Path fullPath = booksHelper.getFullPath(b.book);
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
		descriptionText.getEngine().loadContent(b.description);
		statusText.setText(b.book.getStatus() == null ? null : b.book.getStatus().toString());
		
		descriptionText.getEngine().setUserStyleSheetLocation(ClassLoader.getSystemResource("css/description.css").toString());

		List<String> list = getResources(b);
		List<String> list2;
		try {
			list2 = booksHelper.getResources(b);
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
	}
	private Node hl(Object c, Node node) {
		Hyperlink h = node != null ? (Hyperlink)node : new Hyperlink();

		if(c.getClass() == String.class) {
			h.setText((String)c);
			h.setUserData(c);
		} else {
			Path  p = (Path)c;
			h.setText(p.getFileName().toString());
			h.setTooltip(new Tooltip(c.toString()));
			h.setUserData(p);
		}

		if(node == null) 
			h.setOnAction(e -> open(false, h));
		return h;
	}
	private static String string(int value) {
		return String.valueOf(value);
	}
	public void search0(String str) {
		if(isEmpty(str)) {
			filters.setStringFilter((String)null);
		} else {
			File f;
			if(containsAny(str, '/', '\\') && (f = new File(str)).exists()) {
				String[] names = f.list();
				if(names == null || names.length == 0) {
					FxAlert.showErrorDialog(str, "Dir not found/Dir is empty", null);
					filters.setFalse();  
				} else {
					HashSet<String> set = new HashSet<>();
					for (String s : names) {
						if(s.endsWith(".pdf"))
							set.add(s);
						else if(s.charAt(0) == '_' && s.charAt(s.length() - 1) == '_')
							Optional.ofNullable(new File(f, s).list()).filter(l -> l.length != 0).map(Arrays::asList).ifPresent(set::addAll);
					}
					filters.setStringFilter(set);
				}
			} else {
				filters.setStringFilter(str);
			}
		}

	}
	public void changeStatus() {
		List<SmallBook> books = currentTab.getListView().getSelectionModel().getSelectedItems();
		if(books.isEmpty())
			return;

		BookStatus initial = books.get(0).getStatus();
		ChoiceDialog<BookStatus> c2 = new ChoiceDialog<BookStatus>(initial, BookStatus.values());
		c2.setHeaderText("Change Book Status");
		c2.getDialogPane().setExpandableContent(new TextArea(String.join("\n", Iterables.map(books, b -> b.name))));
		BookStatus status = c2.showAndWait().orElse(null);
		if(status == null || status == initial) {
			FxPopupShop.showHidePopup("cancelled", 2000);
			return;
		}
		booksHelper.changeStatus(books, status);
		filters.setChoiceFilter(statusChoice.getSelected());
	}
	public Book book(SmallBook s) {
		return booksHelper.book(s);
	}
}
