package sam.book.search;

import static sam.books.BooksMeta.BOOK_TABLE_NAME;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.function.Consumer;

import javafx.scene.control.Menu;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import sam.book.BooksHelper;
import sam.fx.alert.FxAlert;
import sam.fx.dialog.TextAreaDialog;
import sam.myutils.Checker;
import sam.myutils.MyUtilsException;
import sam.reference.WeakAndLazy;

public class FiltersMenu extends Menu implements Closeable {
	private Filters filters;
	private BooksHelper booksHelper;
	private Consumer<String> cacheFilterloader;

	public void init(BooksHelper booksHelper, Filters filters, Consumer<String> cacheFilterloader) {
		this.filters = filters;
		this.booksHelper = booksHelper;
		this.cacheFilterloader = cacheFilterloader;

		getItems().addAll(
				sqlFilter(),
				cacheFilter()
				);

		setOnShowing(o -> {
			getItems().forEach(e -> ((ListMenu)e).init());
			setOnShowing(null);
		});
	}
	private Menu sqlFilter() {
		return new ListMenu("sql filters", "sql-filter.txt", true) {
			@Override
			protected void onAction(String text) {
				if(text == null)
					filters.setSQLFilter(null);
				else
					filters.setSQLFilter(MyUtilsException.noError(() -> booksHelper.sqlFilter(text)));
			}

			private final WeakAndLazy<TextAreaDialog> dialog = new WeakAndLazy<>(() -> new TextAreaDialog("filter", "SQL Filter", null));

			@Override
			protected String newValue() {
				TextAreaDialog dialog = this.dialog.get();
				dialog.setContent("SELECT * FROM ");
				String sql = dialog.showAndWait().orElse(null);
				if(Checker.isEmptyTrimmed(sql))
					return null;

				try {
					//check validity
					booksHelper.sqlFilter(sql);
					return sql;
				} catch (SQLException e) {
					FxAlert.showErrorDialog("SELECT * FROM "+BOOK_TABLE_NAME+" WHERE "+sql, "failed sql", e);
					return null;
				}
			}
		};
	}

	private Menu cacheFilter() {
		return new ListMenu("cached filters", "cached-filters.txt", false) {
			@Override
			protected void onAction(String text) {
				if(text != null)
					cacheFilterloader.accept(text);
			}
			@Override
			protected String newLebel() {
				return "-- OPEN -- ";
			}
			private final WeakAndLazy<FileChooser> dialog = new WeakAndLazy<>(() -> {
				FileChooser fc = new FileChooser();
				fc.getExtensionFilters().add(new ExtensionFilter("cached search", "*.booksearch"));
				fc.setTitle("Choose booksearch to open");
				return fc;
			});

			@Override
			protected String newValue() {
				FileChooser fc = this.dialog.get();
				File file = fc.showOpenDialog(App.getStage());
				if(file == null)
					return null;


				fc.setInitialDirectory(file.getParentFile());
				return file.toString();
			}
		};
	}
	@Override
	public void close() throws IOException {
		getItems().forEach(e -> ((ListMenu)e).close());
	}
}
