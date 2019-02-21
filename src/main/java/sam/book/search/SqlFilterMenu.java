package sam.book.search;

import static sam.books.BooksMeta.BOOK_TABLE_NAME;
import static sam.fx.helpers.FxMenu.menuitem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.List;

import javafx.beans.binding.Bindings;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import sam.book.BooksHelper;
import sam.collection.Iterables;
import sam.fx.alert.FxAlert;
import sam.fx.clipboard.FxClipboard;
import sam.fx.helpers.FxCell;
import sam.fx.helpers.FxConstants;
import sam.fx.popup.FxPopupShop;
import sam.io.serilizers.StringWriter2;
import sam.myutils.Checker;
import sam.myutils.MyUtilsException;
import sam.reference.WeakAndLazy;

public class SqlFilterMenu extends Menu {
	private Filters filters;
	private BooksHelper booksHelper;

	public void init(BooksHelper booksHelper, Filters filters) {
		this.filters = filters;
		this.booksHelper = booksHelper;

		clearFilter.setVisible(false);
		init2();
	}

	private static final Path PATH = Paths.get("sql-filter.txt");

	private final MenuItem clearFilter = menuitem("-- NONE -- ", e -> openFilterAction(null));
	private int baseSize;

	private void init2() {
		MenuItem newFilter = menuitem("-- NEW -- ", e -> newFilter());
		MenuItem cleanup = menuitem("-- CLEANUP -- ", e -> cleanUp());

		getItems().addAll(newFilter,cleanup, clearFilter, new SeparatorMenuItem());
		baseSize = getItems().size();
		cleanup.visibleProperty().bind(Bindings.size(getItems()).greaterThan(baseSize));

		if(Files.exists(PATH)) {
			MyUtilsException.noError(() -> Files.lines(PATH))
			.filter(s -> !Checker.isEmptyTrimmed(s))
			.filter(s -> s.trim().charAt(0) != '#')
			.forEach(s -> getItems().add(newItem(s)));
		}
	}
	private List<MenuItem> sqlsMIs() {
		return getItems().subList(baseSize, getItems().size());
	}

	private  WeakAndLazy<Stage> cleanupStage = new WeakAndLazy<>(this::_cleanpStage);

	private Stage _cleanpStage() {
		Stage stage = new Stage(StageStyle.UTILITY);
		ListView<MenuItem> list = new ListView<>();
		list.setCellFactory(FxCell.listCell(MenuItem::getText));
		list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		list.setId("cleanup-list");
		Button delete = new Button("REMOVE");
		Button copy = new Button("COPY");
		copy.setOnAction(e -> {
			String s = String.join("\n", Iterables.map(list.getSelectionModel().getSelectedItems(), MenuItem::getText));
			FxClipboard.setString(s);
			FxPopupShop.showHidePopup(s, 2000);
		});
		delete.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
		copy.disableProperty().bind(delete.disableProperty());
		
		delete.setOnAction(e -> {
			stage.hide();
			getItems().removeAll(list.getSelectionModel().getSelectedItems());
			try {
				Files.write(PATH,getItems().size() == baseSize ? Iterables.empty() : Iterables.map(sqlsMIs(), MenuItem::getText), StandardOpenOption.CREATE, StandardOpenOption.CREATE);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			if(!getItems().contains(current))
				openFilterAction(null);
		});
		
		HBox b = new HBox(10, copy, delete);
		b.setPadding(FxConstants.INSETS_5);
		b.setAlignment(Pos.CENTER_RIGHT);
		
		Text t = new Text("Select To Remove");
		BorderPane.setMargin(t, FxConstants.INSETS_5);
		
		stage.setScene(new Scene(new BorderPane(list, t, null, b, null)));
		stage.sizeToScene();
		return stage;
	}
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void cleanUp() {
		Stage stage = cleanupStage.get();
		Scene scene = stage.getScene();

		((ListView)scene.lookup("#cleanup-list"))
		.getItems()
		.setAll(sqlsMIs());
		
		stage.show();
	}

	private MenuItem current;

	private void openFilterAction(Object e) {
		current = e == null ? null : (MenuItem) (e instanceof MenuItem ? e : ((Event)e).getSource());
		if(current == null)
			filters.setSQLFilter(null);
		else
			filters.setSQLFilter(MyUtilsException.noError(() -> booksHelper.sqlFilter(current.getText())));

		setStyleClass(current);
	}

	private static final String STYLE_CLASS = "filter_selected";

	private void setStyleClass(MenuItem m) {
		getItems().forEach(s -> s.getStyleClass().remove(STYLE_CLASS));
		clearFilter.setVisible(false);
		if(m != null) {
			m.getStyleClass().remove(STYLE_CLASS);
			m.getStyleClass().add(STYLE_CLASS);
			clearFilter.setVisible(true);
		}
	}

	private void newFilter() {
		TextInputDialog dialog = new TextInputDialog();
		dialog.setContentText("SELECT * FROM"+BOOK_TABLE_NAME+" WHERE ");
		String sql = dialog.showAndWait().orElse(null);
		if(Checker.isEmptyTrimmed(sql))
			FxPopupShop.showHidePopup("cancelled", 2000);
		else {
			try {
				//check validity
				booksHelper.sqlFilter(sql);
			} catch (SQLException e) {
				FxAlert.showErrorDialog("SELECT * FROM "+BOOK_TABLE_NAME+" WHERE "+sql, "failed sql", e);
				return;
			}

			MenuItem item = newItem(sql);
			getItems().add(item);
			openFilterAction(item);

			try {
				StringWriter2.appendText(PATH, sql+"\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
	private MenuItem newItem(String s) {
		return menuitem(s, this::openFilterAction);
	}
}
