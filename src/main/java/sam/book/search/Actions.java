package sam.book.search;

import sam.book.Book;
import sam.book.SmallBook;

public interface Actions {
	void changeStatus();
	void remove(SmallBookTab sbtab);
	Book book(SmallBook s);

}
