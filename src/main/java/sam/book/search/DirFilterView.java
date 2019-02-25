package sam.book.search;

import static sam.fx.helpers.FxButton.button;
import static sam.fx.helpers.FxHBox.buttonBox;
import static sam.fx.helpers.FxHBox.maxPane;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javafx.event.ActionEvent;
import javafx.event.WeakEventHandler;
import javafx.geometry.Insets;
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
	private int count;
	private final BooksHelper booksHelper;
	private final Filters filters;
	private final TextField search = new TextField();
	private final WeakEventHandler<ActionEvent> selectHandler = new WeakEventHandler<>(this::handle);
	private final List<Temp> all;
	
	private static class Temp {
		final PathsImpl path;
		final String key;

		Temp(PathsImpl path) {
			this.path = path;
			this.key = key(path.getPath());
		}
		private static String key(String s) {
			int n = s.indexOf('\\');
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
		
		setBottom(buttonBox(new Text("Dir Filter"), countT,maxPane(),new Text("search"), search, button("Select All", e -> {setAllTrue(filter); list.refresh(); resetCount();}), button("CANCEL", e -> backaction.run()), button("OK", e -> ok(backaction))));
		search.setMaxWidth(Double.MAX_VALUE);
		HBox.setMargin(search, new Insets(0, 10, 0, 10));
		
		this.all = booksHelper.getPaths().stream().map(Temp::new).collect(Collectors.toList());
		this.all.sort(Comparator.comparing((Temp c) -> c == null ? null : c.path.getPath()));
		
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
	
	private void resetCount() {
		count = 0;
		list.getItems().forEach(p -> {
			if(filter.get(p.getPathId()))
				count++;
		});
		countT.setText(prefix+count+suffix);
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

		list.getItems().forEach(p -> {
			if(p.getPath().startsWith(prefix))
				filter.set(p.getPathId(), selected);
		});

		resetCount();
		list.refresh();
	}
}
