package sam.book.search;

import static sam.fx.helpers.FxMenu.menuitem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
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

public abstract class SmallBookTab extends Tab {
	protected final ListView<SmallBook> list = new ListView<>();
	protected final List<SmallBook> allData = new ArrayList<>();
	protected BooksHelper booksHelper;

	public SmallBookTab() {
		setClosable(false);
		setContent(list);
		
		list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		list.setOnContextMenuRequested(this::contextmenu);
	}
	public void setChangeListener(ChangeListener<SmallBook> listener) {
		list.getSelectionModel().selectedItemProperty().addListener(listener);
	}
	public void setBooksHelper(BooksHelper booksHelper) {
		this.booksHelper = booksHelper;
	}
	public List<SmallBook> getAllData() {
		return Collections.unmodifiableList(allData);
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
			if(getClass() == RecentsBookTab.class)
				contextmenu2.getItems().add(menuitem("Remove Selected", e1 -> App.getInstance().remove(this)));
		}
		contextmenu2.show(App.getStage(), e.getScreenX(), e.getScreenY());
	}
	WeakAndLazy<HtmlMaker> htmlMaker = new WeakAndLazy<>(HtmlMaker::new);

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
		
		for (SmallBook s : books) 
			consumer.accept(App.getInstance().book(s));
	}
	public void filter(Filters f) {
		f.applyFilter(list.getItems());
	}
	public void setSorter(Comparator<SmallBook> sorter) {
		if(sorter == null) {
			Comparator<SmallBook> c = Comparator.<SmallBook>comparingInt(s -> s.id).reversed();
			allData.sort(c);
			list.getItems().sort(c);
		} else {
			allData.sort(sorter);
			list.getItems().sort(sorter);	
		}
	}
	
	public IntegerBinding sizeProperty() {
		return Bindings.size(list.getItems());
	}
	public MultipleSelectionModel<SmallBook> getSelectionModel() {
		return list.getSelectionModel();
	}
	public void removeAll(List<SmallBook> books) {
		if(books.isEmpty())
			return;
		
		allData.removeAll(books);
		list.getItems().removeAll(books);
	}
}
