package sam.book.search;

import sam.books.BookStatus;

public enum Status2 {
	ALL(null),
	NONE(BookStatus.NONE),
	READ(BookStatus.READ),
	READING(BookStatus.READING),
	SKIPPED(BookStatus.SKIPPED);
	
	final BookStatus status;
	
	private Status2(BookStatus status) {
		this.status = status;
	}
}
