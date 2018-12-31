package sam.book.search;

import java.util.Collections;
import java.util.stream.Collectors;

import javafx.beans.binding.BooleanExpression;
import sam.book.BooksHelper;

public class MainTab extends SmallBookTab {
	
	public void init(BooksHelper helper) {
		helper.getBooks().stream().forEach(allData::add);
	}
}
