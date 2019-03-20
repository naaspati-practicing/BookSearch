package sam.book;

import static sam.books.BooksMeta.BOOK_TABLE_NAME;
import static sam.books.BooksMeta.CHANGE_LOG_TABLE_NAME;
import static sam.books.BooksMeta.LOG_NUMBER;
import static sam.books.BooksMeta.PATH_TABLE_NAME;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;

import sam.books.BooksDB;
import sam.books.PathsImpl;
import sam.sql.JDBCHelper;

class LoadFromDb {
	SmallBook[] books;
	PathsImpl[] paths;
	int last_log_number;
	
	void loadAll(BooksDB db) throws SQLException {
		ArrayList<Object> list = new ArrayList<>(1500);
		db.collect("SELECT * FROM "+PATH_TABLE_NAME, list, PathsImpl::new);
		
		this.paths = list.toArray(new PathsImpl[list.size()]);
		list.clear();
		
		db.collect(JDBCHelper.selectSQL(BOOK_TABLE_NAME, SmallBook.columns()).toString(), list, SmallBook::new);
		last_log_number = Optional.ofNullable(db.findFirst("SELECT max("+LOG_NUMBER+") FROM "+CHANGE_LOG_TABLE_NAME, rs -> rs.getInt(1))).orElse(0);

		this.books = list.toArray(new SmallBook[list.size()]);
	}
}
