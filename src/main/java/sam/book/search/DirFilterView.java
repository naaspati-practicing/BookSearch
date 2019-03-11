package sam.book.search;

import static sam.fx.helpers.FxButton.button;
import static sam.fx.helpers.FxHBox.buttonBox;
import static sam.fx.helpers.FxHBox.maxPane;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import sam.book.BooksHelper;
import sam.books.PathsImpl;
import sam.myutils.Checker;

// import sam.fx.helpers.IconButton;
public class DirFilterView extends BorderPane {
	private final ListView<Temp> list = new ListView<>();
	private BitSet filter;
	private final Text countT = new Text();
	private final String suffix, prefix;
	private final BooksHelper booksHelper;
	private final Filters filters;
	private final Button selectButton;
	private final TextField search = new TextField();
	private final EventHandler<ActionEvent> selectHandler = this::handle;
	private final List<Temp> all;
	
	private static class Temp {
		final PathsImpl path;
		final String key;

		Temp(PathsImpl path) {
			this.path = path;
			this.key = key(path.getPath());
		}
		private static String key(String s) {
			int n = s.lastIndexOf('\\');
			if(n < 0)
				return s.toLowerCase();
			return s.substring(n+1).toLowerCase();
		}
		int getPathId() {
			return path.getPathId();
		}
		String getPath() {
			return path.getPath();
		}
		boolean test(String substring) {
			return key.contains(substring);
		}
	}

	public DirFilterView(Filters filters, BooksHelper booksHelper, Runnable backaction) {
		this.booksHelper = booksHelper;
		this.filters = filters;
		
		setBottom(buttonBox(new Text("Dir Filter"), countT,maxPane(),new Text("search"), search, selectButton = button("Unselect All", this::selectAction), button("CANCEL", e -> backaction.run()), button("OK", e -> ok(backaction))));
		search.setMaxWidth(Double.MAX_VALUE);
		HBox.setMargin(search, new Insets(0, 10, 0, 10));
		
		this.all = booksHelper.getPaths().stream().map(Temp::new).collect(Collectors.toList());
		this.all.sort(Comparator.comparing((Temp c) -> c == null ? null : c.getPath()));
		
		setCenter(list);
		list.setCellFactory(c -> new Lc());
		List<Temp> list = this.list.getItems();
		list.addAll(all);
		
		List<Temp> sink = new ArrayList<>();
		search.textProperty().addListener((p, o, n) -> {
			if(Checker.isEmpty(n))
				this.list.getItems().setAll(this.all);
			else {
				n = n.toLowerCase();
				sink.clear();
				for (Temp t : this.all) {
					if(t.test(n))
						sink.add(t);
				}
				this.list.getItems().setAll(sink);
				sink.clear();
			}
		});

		prefix = "Selected: ";
		suffix = "/"+list.size();
	}

	private void selectAction(ActionEvent e) {
		if(selectButton.getText().equals("Select All")) {
			selectButton.setText("Unselect All");
			list.getItems().forEach(t -> filter.set(t.getPathId()));
		} else {
			selectButton.setText("Select All");
			filter.clear();
		}
		
		list.refresh();
		resetCount();
	}

	private void resetCount() {
		countT.setText(prefix+filter.cardinality()+suffix);
	}

	public void start() {
		filter = Optional.ofNullable(filters.getDirFilter())
				.map(DirFilter::copy)
				.orElseGet(() -> {
					BitSet filter = new BitSet();
					setAllTrue(filter);
					return filter;
				});
		
		list.refresh();
		resetCount();
	}
	private void setAllTrue(BitSet filter) {
		booksHelper.getPaths().forEach(p -> filter.set(p.getPathId()));
	}
	private void ok(Runnable backAction) {
		filters.setDirFilter(new DirFilter(filter));
		backAction.run();
	}

	private class Lc extends ListCell<Temp> {
		private final CheckBox c = new CheckBox();
		Temp path;

		{
			c.setOnAction(selectHandler);
			c.setUserData(this);
		}

		@Override
		protected void updateItem(Temp item, boolean empty) {
			if(item != null && item == path) {
				c.setSelected(filter.get(path.getPathId()));
				return;
			}

			super.updateItem(item, empty);

			if(empty || item == null) {
				setText(null);
				setGraphic(null);
				path = null;
			} else {
				path = item;

				setText(path.getPath());
				setGraphic(c);
				c.setSelected(filter.get(path.getPathId()));
			}
		}
	}

	public void handle(ActionEvent e) {
		CheckBox c = (CheckBox) e.getSource();
		Lc item = (Lc) c.getUserData();
		boolean selected = c.isSelected();
		String prefix = item.path.getPath().concat("\\");
		filter.set(item.path.getPathId(), selected);
		
		int n = all.indexOf(item.path) + 1;
		
		while(n < all.size()) {
			Temp t = all.get(n++);
			if(t.getPath().startsWith(prefix))
				filter.set(t.getPathId(), selected);
			else
				break;
		}
		
		resetCount();
		list.refresh();
	}
}
