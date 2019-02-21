package sam.book.search;

import static sam.fx.helpers.FxButton.button;
import static sam.fx.helpers.FxHBox.buttonBox;
import static sam.fx.helpers.FxHBox.maxPane;

import java.util.BitSet;
import java.util.Comparator;
import java.util.Optional;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import sam.book.BooksHelper;
import sam.books.PathsImpl;

// import sam.fx.helpers.IconButton;
public class DirFilterView extends BorderPane implements EventHandler<ActionEvent> {
	private final ListView<PathsImpl> list = new ListView<>();
	private BitSet filter;
	private final Text countT = new Text();
	private final String suffix, prefix;
	private int count;
	private final BooksHelper booksHelper;
	private final Filters filters;

	public DirFilterView(Filters filters, BooksHelper booksHelper, Runnable backaction) {
		this.booksHelper = booksHelper;
		this.filters = filters;
		
		setBottom(buttonBox(new Text("Dir Filter"), countT, maxPane(), button("Select All", e -> {setAllTrue(filter); list.refresh(); resetCount();}), button("CANCEL", e -> backaction.run()), button("OK", e -> ok(backaction))));
		setCenter(list);
		list.setCellFactory(c -> new Lc());
		booksHelper.getPaths().forEach(list.getItems()::add);
		list.getItems().sort(Comparator.comparing(c -> c.getPath()));

		prefix = "Selected: ";
		suffix = "/"+list.getItems().size();
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

	private class Lc extends ListCell<PathsImpl> {
		private final CheckBox c = new CheckBox();
		PathsImpl path;

		{
			c.setOnAction(DirFilterView.this);
			c.setUserData(this);
		}

		@Override
		protected void updateItem(PathsImpl item, boolean empty) {
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

	@Override
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
