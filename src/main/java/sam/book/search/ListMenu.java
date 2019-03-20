package sam.book.search;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static sam.fx.helpers.FxMenu.menuitem;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import sam.collection.Iterables;
import sam.collection.MappedIterator;
import sam.fx.clipboard.FxClipboard;
import sam.fx.helpers.FxCell;
import sam.fx.helpers.FxConstants;
import sam.fx.popup.FxPopupShop;
import sam.myutils.Checker;
import sam.reference.WeakAndLazy;

abstract class ListMenu extends Menu implements Closeable {
	private static final Logger logger = LoggerFactory.getLogger(ListMenu.class);
	
	private List<String> data;
	private List<String> new_data;
	private final Path path;
	private final boolean encoded;
	private boolean modified;
	private int skipSize;

	public ListMenu(String title, Path path, boolean encoded) {
		super(title);
		this.path = path;
		this.encoded = encoded;
	}
	
	public ListMenu(String title, String path, boolean encoded) {
		this(title, Paths.get(path), encoded);
	}
	
	private final MenuItem clearFilter = menuitem("-- NONE -- ", e -> openFilterAction(null));

	public void init() {
		if(data == null) {
			Function<String, String> mapper;
			if(encoded) {
				Decoder e = Base64.getDecoder();
				mapper = s -> new String(e.decode(s));
			} else {
				mapper = s -> s;
			}

			try {
				this.data = Files.notExists(path) ? Collections.emptyList() : 
					Files.lines(path)
					.filter(Checker::isNotEmptyTrimmed)
					.map(mapper)
					.peek(s -> logger.debug(s))
					.collect(Collectors.collectingAndThen(Collectors.toList(), s -> s.isEmpty() ? Collections.emptyList() : s));
				logger.debug("loaded: {}", path);
			} catch (IOException e) {
				logger.error("failed to load: {}", path, e);
				setDisable(true);
				data = Collections.emptyList();
				return;
			}
		}
		
		MenuItem newFilter = menuitem(newLebel(), e -> newFilter());
		MenuItem cleanup = menuitem("-- CLEANUP -- ", e -> cleanUp());
		
		clearFilter.setVisible(false);

		getItems().addAll(newFilter,cleanup, clearFilter, new SeparatorMenuItem());
		this.skipSize = getItems().size();
		cleanup.visibleProperty().bind(Bindings.size(getItems()).greaterThan(skipSize));
		
		data.forEach(s -> getItems().add(newItem(s)));
	}

	private class CleanupStage extends Stage {
		final ListView<MenuItem> list = new ListView<>();
		
		public CleanupStage() {
			super(StageStyle.UTILITY);
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
			
			delete.setOnAction(d -> {
				list.getSelectionModel().getSelectedItems()
				.forEach(m -> {
					getItems().remove(m);
					modified = data.remove(m.getText()) || modified;
				});
			});
			
			HBox b = new HBox(10, copy, delete);
			b.setPadding(FxConstants.INSETS_5);
			b.setAlignment(Pos.CENTER_RIGHT);
			
			Text t = new Text("Select To Remove");
			BorderPane.setMargin(t, FxConstants.INSETS_5);
			
			setScene(new Scene(new BorderPane(list, t, null, b, null)));
			sizeToScene();
		}
	}
	
	private  WeakAndLazy<CleanupStage> cleanupStage = new WeakAndLazy<>(CleanupStage::new);

	private void cleanUp() {
		CleanupStage stage = cleanupStage.get();
		stage.list.getItems().setAll(getItems());
		stage.show();
	}

	private MenuItem current;

	private void openFilterAction(Object e) {
		current = e == null ? null : (MenuItem) (e instanceof MenuItem ? e : ((Event)e).getSource());
		onAction(current.getText());
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
		String s = newValue();
		if(s == null || data.contains(s) || (new_data != null && new_data.contains(s)))
			return;
		
		if(new_data == null)
			new_data = new ArrayList<>();
		new_data.add(s);
		
		MenuItem item = newItem(s);
		getItems().add(item);
		openFilterAction(item);
	}
	
	protected String newLebel() {
		return "-- NEW -- ";
	}
	protected abstract void onAction(String text);
	protected abstract String newValue();
	
	private MenuItem newItem(String s) {
		return menuitem(s, this::openFilterAction);
	}
	
	@Override
	public void close() {
		if(!modified && Checker.isEmpty(new_data))
			return ;
		
		Function<String, String> mapper;
		if(encoded) {
			Encoder e = Base64.getEncoder();
			mapper = s -> e.encodeToString(s.getBytes());
		} else {
			mapper = s -> s;
		}
		
		try(BufferedWriter w = Files.newBufferedWriter(path, CREATE, WRITE, modified ? TRUNCATE_EXISTING : APPEND)) {
			Iterator<String> str;
			if(modified) 
				str = new MappedIterator<>(getItems().subList(skipSize, getItems().size()).iterator(), m -> m.getText());
			 else 
				str = new_data.iterator();
			
			while (str.hasNext()) {
				String s = str.next();
				w.write(mapper.apply(s));
				w.append('\n');
			}
			logger.debug("saved: {}", path);
		} catch (Exception e) {
			logger.error("failed to save: {}", path, e);
		}
	}

}
