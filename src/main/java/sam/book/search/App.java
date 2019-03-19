package sam.book.search;
import static sam.book.search.ResourceHelper.RESOURCE_DIR;
import static sam.book.search.ResourceHelper.getResources;
import static sam.myutils.Checker.isEmpty;
import static sam.string.StringUtils.containsAny;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import sam.book.Book;
import sam.book.BooksHelper;
import sam.book.SmallBook;
import sam.books.BookStatus;
import sam.books.BooksDB;
import sam.collection.CollectionUtils;
import sam.collection.Iterables;
import sam.config.MyConfig;
import sam.console.ANSI;
import sam.fx.alert.FxAlert;
import sam.fx.clipboard.FxClipboard;
import sam.fx.helpers.FxButton;
import sam.fx.helpers.FxCell;
import sam.fx.helpers.FxChoiceBox;
import sam.fx.helpers.FxFxml;
import sam.fx.helpers.FxHBox;
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.FileOpener;
import sam.io.fileutils.FileOpenerNE;
import sam.io.serilizers.StringWriter2;
import sam.myutils.Checker;
import sam.myutils.MyUtilsPath;
import sam.nopkg.EnsureSingleton;
import sam.reference.WeakAndLazy;

public class App extends Application implements ChangeListener<SmallBook>, Actions {
	private static final EnsureSingleton singleton = new EnsureSingleton();
	{ singleton.init(); }
	private static final Logger logger = LoggerFactory.getLogger(Filters.class);

	public static String startSearchFile;

	// bookId  -> book
	private BooksHelper booksHelper;

	@FXML FiltersMenu filtersMenu;
	@FXML Scene mainScene;
	@FXML private Text searchLabel;
	@FXML private TextField searchField;
	@FXML private TabPane tabpane;
	@FXML private MainTab allTab;
	@FXML private RecentsBookTab recentTab;
	@FXML private Text countText;
	@FXML private Text loadText;
	@FXML private VBox vbox;
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
	@FXML private SplitPane mainRoot;
	@FXML private VBox resourceBox;

	private Sorter currentSorter = Sorter.DEFAULT;
	private boolean currentSorter_reversed = false;

	private final Filters filters = new Filters();
	private Book currentBook;
	private SmallBookTab currentTab;
	private static Stage mainStage;
	private static Actions actions;

	private static int _n = 0;
	private static final int MANUAL_TEXT_SET = _n++;
	private static final int MANUAL_CHOICE_SET  = _n++;
	private static final int MANUAL_SORT_SET  = _n++;
	private final boolean[] manualSet = new boolean[_n];
	private Runnable runafterFilter;

	public static Window getStage() {
		return mainStage;
	}

	@Override
	public void start(Stage stage) throws Exception {
		App.mainStage = stage;
		actions = this;
		FxFxml.load(ClassLoader.getSystemResource("fxml/App.fxml"), stage, this);
		reset(null);

		FxAlert.setParent(stage);
		FxPopupShop.setParent(stage);

		countText.textProperty().bind(allTab.sizeProperty().asString());
		resourceBox.visibleProperty().bind(Bindings.isNotEmpty(resourceBox.getChildren()));

		Platform.runLater(() -> mainRoot.setDividerPositions(0.4, 0.6));
		prepareChoiceBoxes();

		searchField.textProperty().addListener((p, o, n) -> {
			if(manualSet[MANUAL_TEXT_SET])
				manualSet[MANUAL_TEXT_SET] = false;
			else {
				currentTab.getSelectionModel().clearSelection();
				search0(n);
			}
		});
		stage.show();

		booksHelper = new BooksHelper();
		allTab.init(booksHelper);
		recentTab.init(booksHelper::getSmallBook);

		forEachSmallBookTab(t -> {
			t.setSorter(sorter(currentSorter));
			t.setBooksHelper(booksHelper);
		});

		this.allTab.setChangeListener(this);
		this.recentTab.setChangeListener(this);

		tabpane.getSelectionModel().selectedItemProperty().addListener((p, o, n) -> {
			currentTab = (SmallBookTab)n;
			countText.textProperty().bind(currentTab.sizeProperty().asString());
			filters.setAllData(currentTab.getAllData());
		});

		filtersMenu.init(booksHelper, filters, this::loadFilter);
		mainScene.getStylesheets().add("css/style.css");

		Platform.runLater(() -> {
			currentTab = (SmallBookTab) tabpane.getSelectionModel().getSelectedItem();
			filters.setAllData(currentTab.getAllData());
			filters.setOnChange(() -> {
				currentTab.filter(filters);
				
				if(runafterFilter != null) {
					Runnable r = runafterFilter;
					runafterFilter = null;
					Platform.runLater(r);
				}
			});
			currentTab.setSorter(sorter(currentSorter));
			currentTab.filter(filters);

			if(startSearchFile != null)
				Platform.runLater(() -> loadFilter(startSearchFile));

			filters.enable();
		});
	}

	private Comparator<SmallBook> sorter(Sorter s) {
		return s == null ? null : s.sorter;
	}

	private void loadFilter(String file) {
		try {
			Path p = Paths.get(file);

			if(Files.notExists(p))
				FxAlert.showErrorDialog(file, "File not found", null);
			else  {
				Arrays.fill(manualSet, true);
				AppState f = new AppState();
				f.read(p);

				this.currentGrouping = f.grouping;
				this.currentSorter = f.sorter;
				this.currentSorter_reversed = f.sorter_revered;

				filters.loadFilters(f);
				searchField.setText(filters.getSearchString());
				statusChoice.select(filters.choice());
				sortChoice.select(this.currentSorter);
				
				if(this.currentGrouping != null)
					runafterFilter = () -> groupByAction(null);
			}
		} catch (Exception e) {
			FxAlert.showErrorDialog(file, "failed to load filter", e);
		}
	}

	@SuppressWarnings("rawtypes")
	private class Grouper extends BorderPane implements ChangeListener<SmallBook> {
		private final ChoiceBox<Grouping> choice;
		private final ListView<Object[]> left = new ListView<>();
		private final ListView<SmallBook> center = new ListView<>();
		private final Button back = new Button("back");

		private final Map<Comparable, List<SmallBook>> map = new TreeMap<>();
		private final ChangeListener<Object[]> leftListener;
		private final ChangeListener<Grouping> choiceLister;
		private List<SmallBook> list;
		private Node previous;

		public Grouper() {
			choice = FxChoiceBox.choiceBox(Grouping.values(), Grouping::valueOf, true);

			setBottom(FxHBox.buttonBox(
					new Text("Group By: "), 
					FxButton.button("As Html", e -> saveAsHtml(e), Bindings.isEmpty(left.getItems())),
					FxButton.button("As .booksearch", e -> saveFilter(e), Bindings.isEmpty(left.getItems())),
					choice
					) );
			
			setTop(back);
			back.setOnAction(e -> hide());
			BorderPane.setMargin(back, new Insets(2, 5, 2, 5));

			SplitPane sp = new SplitPane(left, center);
			sp.setDividerPositions(0.3, 0.6);
			setCenter(sp);

			this.leftListener = (p, o, n) -> setCenter(n == null ? null : map.get(n[0]));
			this.choiceLister = (p, o, n) -> reset();

			left.setCellFactory(FxCell.listCell(s -> (String)s[1]));

		}
		public void show(List<SmallBook> list, Grouping current) {
			this.list = list;
			this.previous = App.this.setLeft(this);

			set();
			reset();
			
			if(current != null)
				Platform.runLater(() -> choice.getSelectionModel().select(current));
		}
		void hide() {
			unset();
			App.this.setLeft(previous);
			currentGrouping = null;
		}
		private void set() {
			center.getSelectionModel().selectedItemProperty().addListener(this);
			left.getSelectionModel().selectedItemProperty().addListener(leftListener);
			choice.getSelectionModel().selectedItemProperty().addListener(choiceLister);
		}
		public void unset() {
			center.getSelectionModel().selectedItemProperty().removeListener(this);
			left.getSelectionModel().selectedItemProperty().removeListener(leftListener);
			choice.getSelectionModel().selectedItemProperty().removeListener(choiceLister);
		}
		@SuppressWarnings("unchecked")
		private void reset() {
			Grouping g = choice.getSelectionModel().getSelectedItem();
			currentGrouping = g;
			
			if(g == null) {
				left.getSelectionModel().clearSelection();
				left.getItems().clear();
			} else {
				map.forEach((s,t) -> t.clear());
				list.stream().collect(Collectors.groupingBy(g.mapper, () -> map, Collectors.toList()));
				left.getSelectionModel().clearSelection();
				left.getItems().clear();

				map.forEach((s,t) -> {
					if(!t.isEmpty())
						left.getItems().add(new Object[]{s, String.format("%-6s (%s)", s, t.size())});
				});

				left.getItems().sort(Comparator.comparing(c -> (Comparable)c[0]));
			}
		}
		@Override
		public void changed(ObservableValue<? extends SmallBook> observable, SmallBook oldValue, SmallBook newValue) {
			if(newValue != null)
				currentTab.list.getSelectionModel().select(newValue);
		}
		public void setCenter(List<SmallBook> list) {
			center.getSelectionModel().clearSelection();
			if(Checker.isEmpty(list))
				center.getItems().clear();
			else
				center.getItems().setAll(list);
		}
	}

	private Parent dirFilterPrevious;
	private final WeakAndLazy<DirFilterView> wdirFilter = new WeakAndLazy<>(() -> new DirFilterView(filters, booksHelper, () -> {mainScene.setRoot(dirFilterPrevious); dirFilterPrevious = null; }));

	@FXML
	private void dirFilterAction(ActionEvent e) {
		dirFilterPrevious = mainScene.getRoot();
		DirFilterView d = wdirFilter.get();
		mainScene.setRoot(d);
		d.start();
	}
	private final WeakAndLazy<Grouper> grouper = new WeakAndLazy<>(Grouper::new);

	@FXML
	private void groupByAction(ActionEvent e) {
		List<SmallBook> list = Optional.ofNullable(currentTab).map(c -> c.list).map(ListView::getItems).filter(Checker::isNotEmpty).orElse(null);
		if(Checker.isEmpty(list)) {
			FxPopupShop.showHidePopup("nothing to group", 1500);
			return;
		}
		grouper.get().show(list, this.currentGrouping);
		this.currentGrouping = null;
	}
	public Node setLeft(Node node) {
		return mainRoot.getItems().set(0, node);
	}
	public Node getLeft() {
		return mainRoot.getItems().get(0);
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
		.addListener((p, o, n) -> {
			if(manualSet[MANUAL_CHOICE_SET])
				manualSet[MANUAL_CHOICE_SET] = false;
			else
				filters.setChoiceFilter(n);
		});

		sortChoice.init(Sorter.values());
		sortChoice.selectedProperty().addListener((p, o, n) -> {
			currentSorter = n;

			if(manualSet[MANUAL_SORT_SET])
				manualSet[MANUAL_SORT_SET] = false;
			else 
				currentSorter_reversed = o == n ? !currentSorter_reversed : false;

			Comparator<SmallBook> c = currentSorter_reversed ? n.sorter.reversed() : n.sorter;
			forEachSmallBookTab(t -> t.setSorter(c));
		});
		currentSorter = sortChoice.getSelected();
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
					recentTab.add(currentBook);
				} else
					FileOpener.openFileLocationInExplorer(p.toFile());
			} catch (IOException e1) {
				FxAlert.showErrorDialog(p, "Failed to open", e1);
			}
		}
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

		filtersMenu.close();

		if(booksHelper != null)
			booksHelper.close();

		recentTab.close();
		statusChoice.close();
		sortChoice.close();
	}

	private void addResourceLinks(List<String> resource) {
		resource.forEach(p -> resourceBox.getChildren().add(hl(p.charAt(0) == '\\' ? RESOURCE_DIR.resolve(p.substring(1)) : p, null)));
	}

	WeakAndLazy<Stage> resourceChoice = new WeakAndLazy<>(this::resourceChoiceStage);

	private Stage resourceChoiceStage() {
		Stage s = new Stage(StageStyle.UTILITY);
		s.initOwner(mainStage);
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

		List<File> files = fc.showOpenMultipleDialog(mainStage);

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
		dialog.initOwner(mainStage);
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
		copyToClip(dirname(currentBook));
	}
	String dirname(Book b) {
		return BooksDB.createDirname(b.book.id, b.book.filename);
	}
	private void copyToClip(String s) {
		FxClipboard.setString(s);
		FxPopupShop.showHidePopup("copied: "+s, 1500);
	}
	@Override
	public void remove(SmallBookTab tab) {
		List<SmallBook> books = tab.getSelectionModel().getSelectedItems();
		if(books.isEmpty())
			return;

		books = CollectionUtils.copyOf(books);
		tab.getSelectionModel().clearSelection();

		tab.removeAll(books);
	}

	private File saveFilter_parent;
	private Grouping currentGrouping;
	@FXML
	private void saveFilter(Event e) {
		FileChooser fc = new FileChooser();
		fc.getExtensionFilters().add(new ExtensionFilter("cached search", "*.booksearch"));
		fc.setTitle("Choose booksearch to save");
		if(saveFilter_parent != null)
			fc.setInitialDirectory(saveFilter_parent);

		File file = fc.showSaveDialog(getStage());

		if(file == null)
			FxPopupShop.showHidePopup("cancelled", 1500);
		else {
			saveFilter_parent = file.getParentFile();
			try {
				AppState f = new AppState();

				f.grouping = currentGrouping;
				f.sorter = currentSorter;
				f.sorter_revered = currentSorter_reversed;

				filters.save(f);

				Path p = file.toPath();
				logger.debug("filters saved : {}\n{}", p, f);
				f.write(p);
			} catch (IOException e1) {
				FxAlert.showErrorDialog(file, "failed to save filter", e);
			}
		}
	}

	@FXML
	private void saveAsHtml(Event e) {
		FileChooser fc = new FileChooser();
		fc.setInitialDirectory(new File(MyConfig.COMMONS_DIR));
		fc.setInitialFileName("booklist.html");
		fc.getExtensionFilters().setAll(new ExtensionFilter("html", "*.html"));
		fc.setTitle("save as");

		File file = fc.showSaveDialog(App.getStage());
		if(file == null) {
			FxPopupShop.showHidePopup("cancelled", 1500);
			return;
		}

		Node node = getLeft();
		StringBuilder sb;
		if(node.getClass() == Grouper.class) {
			Grouper g = (Grouper) node;
			sb = HtmlMaker.toHtml(g.map, booksHelper);
		} else {
			sb = HtmlMaker.toHtml(currentTab.getItems(), booksHelper);
		}
		try {
			StringWriter2.setText(file.toPath(), sb);
			FileOpenerNE.openFileLocationInExplorer(file);
		} catch (IOException e1) {
			FxAlert.showErrorDialog(file, "failed to save html", e1);
		}
	}

	@FXML
	private void reloadResoueces(Event e) {
		((MenuItem )e.getSource()).setDisable(true);
		ResourceHelper.reloadResoueces();
	}
	private void reset(SmallBook n) {
		vbox.setVisible(n != null);
		if(n == null) return;

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
		descriptionText.getEngine().loadContent(Checker.isEmpty(b.description) ? "NO DESCRIPTION" : b.description);
		statusText.setText(b.book.getStatus() == null ? null : b.book.getStatus().toString());

		descriptionText.getEngine().setUserStyleSheetLocation(ClassLoader.getSystemResource("css/description.css").toString());

		List<Path> list = getResources(b);
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

		System.out.println("change view: "+dirname(b));
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
	private void search0(String str) {
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
	@Override
	public void changeStatus() {
		List<SmallBook> books = currentTab.getSelectionModel().getSelectedItems();
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
	@Override
	public Book book(SmallBook s) {
		return booksHelper.book(s);
	}
	@Override
	public void changed(ObservableValue<? extends SmallBook> observable, SmallBook oldValue, SmallBook newValue) {
		reset(newValue);
	}

	public static Actions actions() {
		return actions;
	}

}
