package sam.book.search;

import sam.book.BooksHelper;

public class MainTab extends SmallBookTab {
	
	public void init(BooksHelper helper) {
		helper.getBooks().stream().forEach(allData::add);
	}
}
