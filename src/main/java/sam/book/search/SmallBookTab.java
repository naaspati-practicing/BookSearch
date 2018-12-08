package sam.book.search;

import static sam.fx.helpers.FxMenu.menuitem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.input.ContextMenuEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import sam.book.Book;
import sam.book.BooksHelper;
import sam.book.SmallBook;
import sam.config.MyConfig;
import sam.fx.alert.FxAlert;
import sam.fx.popup.FxPopupShop;
import sam.reference.WeakAndLazy;

public class SmallBookTab extends Tab {
	private final ListView<SmallBook> list = new ListView<>();
	private List<SmallBook> allData = new ArrayList<>();
	private TabType type;
	private BooksHelper booksHelper;

	public SmallBookTab() {
		setClosable(false);
		setContent(list);

		list.getSelectionModel().selectedItemProperty().addListener((p, o, n) -> App.getInstance().reset(n, type));
		list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		list.setOnContextMenuRequested(this::contextmenu);
	}
	public void setBooksHelper(BooksHelper booksHelper) {
		this.booksHelper = booksHelper;
	}
	public void setType(TabType type) {
		this.type = type;
	}
	public TabType getType() {
		return type;
	}
	public List<SmallBook> getAllData() {
		return allData;
	}
	public void setAllData(List<SmallBook> allData) {
		this.allData = allData;
	}
	public ListView<SmallBook> getListView() {
		return list;
	}

	private ContextMenu contextmenu2;

	private void contextmenu(ContextMenuEvent e) {
		if(list.getSelectionModel().getSelectedItem() == null)
			return;

		if(contextmenu2 == null) {
			contextmenu2 = new ContextMenu(
					menuitem("Copy Selected", e1 -> copyselected()),
					menuitem("Change Status", e1 -> App.getInstance().changeStatus()),
					menuitem("Save HTML", e1 -> saveHtml(), list.getSelectionModel().selectedItemProperty().isNull())
					);
			if(type == TabType.RECENT)
				contextmenu2.getItems().add(menuitem("Remove Selected", e1 -> App.getInstance().remove(this)));
		}
		contextmenu2.show(App.getStage(), e.getScreenX(), e.getScreenY());
	}
	
	WeakAndLazy<HtmlMaker> htmlMaker = new WeakAndLazy<>(HtmlMaker::new);
	private Comparator<SmallBook> comparator;

	private void saveHtml() {
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
		
		try {
			htmlMaker.get().create(list.getSelectionModel().getSelectedItems(), file.toPath());
		} catch (IOException e) {
			FxAlert.showErrorDialog(file, "Failed to save "+file.getName(), e);
		}
	}
	private void copyselected() {
		DirectoryChooser dc = new DirectoryChooser();
		dc.setInitialDirectory(new File(MyConfig.COMMONS_DIR));
		dc.setTitle("choose dir");
		File file = dc.showDialog(App.getStage());

		if(file == null)
			FxPopupShop.showHidePopup("cancelled", 1500);

		Path root = file.toPath();
		try {
			Files.createDirectories(root);
		} catch (IOException e) {
			FxAlert.showErrorDialog(root, "ERROR creating dir", e);
			return ;
		}

		int count[] = {0};
		each(list, book -> {
			Path source = null, target = null;
			try {
				source = booksHelper.getFullPath(book.book);
				target = root.resolve(source.getFileName());

				Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
				count[0]++;
			} catch (IOException|NullPointerException e) {
				FxAlert.showErrorDialog("source: "+source+"\ntarget:"+target, "failed to copy", e);
			}  
		});
		FxPopupShop.showHidePopup("Copied: "+count, 1500);
	}

	private void each(ListView<SmallBook> listview, Consumer<Book> consumer) {
		List<SmallBook> books = listview.getSelectionModel().getSelectedItems();
		for (SmallBook s : books) {
			consumer.accept(App.getInstance().book(s));
		}
	}
	public void filter(Filters f) {
		f.applyFilter(list.getItems());
		list.getItems().sort(comparator);
	}
	public void setSorter(Comparator<SmallBook> comparator) {
		this.comparator = comparator;
		list.getItems().sort(comparator);
	}
}
