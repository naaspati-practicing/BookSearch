package sam.book;

import sam.books.BookStatus;

class TempSMB {
	/**
	 * id + page_count + year + path_id + status
	 */
    static final int BYTES =  5 * Integer.BYTES ;
	
	public int id, page_count, year, path_id;
    public BookStatus status;
}
