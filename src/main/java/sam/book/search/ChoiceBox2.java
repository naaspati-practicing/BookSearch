package sam.book.search;

import static sam.fx.helpers.FxClassHelper.setClass;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import javafx.beans.NamedArg;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.EventHandler;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import sam.fx.helpers.FxBindings;
import sam.io.serilizers.IntSerializer;
import sam.reference.WeakAndLazy;

public class ChoiceBox2<E> extends VBox implements EventHandler<javafx.event.ActionEvent>, Closeable {
	private final Text title = new Text();
	private final Hyperlink link = new Hyperlink();
	private final ReadOnlyObjectWrapper<E> selected = new ReadOnlyObjectWrapper<>();
	private E[] data;

	public ChoiceBox2(@NamedArg("title") String title) {
		setClass(this.title, "title");
		setClass(this, "choicebox2");

		this.title.setText(title);
		getChildren().addAll(this.title, link);

		link.setOnAction(this);
		link.setFocusTraversable(false);
		link.textProperty().bind(FxBindings.<E, String>map(selected, s -> s == null ? null : s.toString().toLowerCase()));
	}
	private final WeakAndLazy<ChoiceDialog<E>> dialog = new WeakAndLazy<>(this::newDialog);

	private ChoiceDialog<E> newDialog() {
		ChoiceDialog<E> d = new ChoiceDialog<>(getSelected(), data);
		d.setHeaderText(title.getText());
		d.initModality(Modality.APPLICATION_MODAL);
		d.initOwner(App.getStage());
		return d;
	}
	@Override
	public void handle(javafx.event.ActionEvent event) {
		ChoiceDialog<E> d = dialog.get();
		d.setSelectedItem(getSelected());
		d.showAndWait().ifPresent(this::select);
		link.setVisited(false);
	}
	public ReadOnlyObjectProperty<E> selectedProperty() {
		return selected.getReadOnlyProperty();
	}
	public void select(E e) {
		selected.set(e);
	}
	private boolean init;
	public void init(E[] values) throws IOException {
		Objects.requireNonNull(values);
		if(init) throw new IllegalStateException("already initialized");
		init = true;
		this.data = values;
		Path p = Paths.get(values.getClass().getComponentType().getName());
		select(Files.notExists(p) ? values[0] : values[IntSerializer.read(p)]);
	}
	public E getSelected() {
		return selected.get();
	}
	@SuppressWarnings("rawtypes")
	@Override
	public void close() throws IOException {
		Path p = Paths.get(getSelected().getClass().getName());
		IntSerializer.write(((Enum)getSelected()).ordinal(), p);
	}
	
}
