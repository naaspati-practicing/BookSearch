package sam.book.search;

import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import sam.book.BooksHelper;
import sam.book.SmallBook;
import sam.books.PathsImpl;
import sam.fx.helpers.IconButton;
import sam.reference.WeakAndLazy;

public class DirFilter extends IconButton implements EventHandler<ActionEvent>, Predicate<SmallBook> {
	private final BitSet selected = new BitSet();
	private BooksHelper booksHelper;
	
	public DirFilter() {
		setOnAction(this);
	}
	private final WeakAndLazy<Stage> dialog = new WeakAndLazy<>(this::newDialog);
	private Filters filters;
	
	public void init(BooksHelper booksHelper, Filters filters) {
		this.booksHelper = booksHelper;
		this.filters = filters;
		this.booksHelper.getPaths().forEach(p -> selected.set(p.getPathId()));
	}
	
	private static class Wrap {
		private final PathsImpl path;
		private CheckBox checkBox;
		
		public Wrap(PathsImpl path) {
			this.path = path;
		}
		public void set(CheckBox c) {
			if(c.getUserData() != null)
				((Wrap)c.getUserData()).checkBox = null;
			
			c.setUserData(this);
			this.checkBox = c;
		}
	}
	
	int cn = 0;
	private Stage newDialog() {
		Stage s = new Stage(StageStyle.UTILITY);
		s.initModality(Modality.APPLICATION_MODAL);
		s.initOwner(App.getStage());
		
		Label path = new Label();
		path.setWrapText(true);
		
		ObservableList<Wrap> wraps = booksHelper.getPaths().stream().map(Wrap::new).collect(Collectors.toCollection(FXCollections::observableArrayList));
		ListView<Wrap> list = new ListView<>(wraps);
		list.getItems().sort(Comparator.comparing(p -> p.path.getPath()));
		
		list.setCellFactory(cell -> new ListCell<Wrap>() {
			private final CheckBox checkBox = new CheckBox();
			
			{
				checkBox.setOnAction(DirFilter.this);
			}

			@Override
			protected void updateItem(Wrap item, boolean empty) {
				super.updateItem(item, empty);
				if(empty || item == null)
					setGraphic(null);
				else {
					setGraphic(checkBox);
					checkBox.setText(item.path.getMarker());
					checkBox.setSelected(selected.get(item.path.getPathId()));
					item.set(checkBox); 
				}
			}
		});
		
		list.getSelectionModel()
		.selectedItemProperty()
		.addListener((p, o, n) -> path.setText(n == null ? null : n.path.getPath()));
		
		path.setPadding(new Insets(5));
		s.setScene(new Scene(new BorderPane(list, null, null, path, null)));
		return s;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void handle(ActionEvent e) {
		if(e.getSource() == this) {
			dialog.get().showAndWait();
			filters.setDirFilter(this);
		} else {
			CheckBox c = (CheckBox) e.getSource();
			Wrap item = (Wrap) c.getUserData();
			boolean s = c.isSelected();
			String path = item.path.getPath()+"\\";
			
			ListView<Wrap> listview = (ListView<Wrap>) ((BorderPane)dialog.get().getScene().getRoot()).getCenter();
			List<Wrap> list = listview.getItems();

			int n = 0;
			for (; n < list.size(); n++) {
				if(list.get(n) == item)
					break;
			}
			n++;
			for (; n < list.size(); n++) {
				Wrap t = list.get(n);
				
				if(t.path.getPath().startsWith(path)) {
					selected.set(t.path.getPathId(), s);
					if(t.checkBox != null)
						t.checkBox.setSelected(s);
				} else 
					break;
			}
		}
	}
	@Override
	public boolean test(SmallBook t) {
		return selected.get(t.path_id);
	}
}
