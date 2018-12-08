package sam.book;
import static sam.books.BooksMeta.AUTHOR;
import static sam.books.BooksMeta.BOOK_ID;
import static sam.books.BooksMeta.BOOK_TABLE_NAME;
import static sam.books.BooksMeta.CREATED_ON;
import static sam.books.BooksMeta.DESCRIPTION;
import static sam.books.BooksMeta.ISBN;
import static sam.books.BooksMeta.URL;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

import sam.books.BooksDB;
import sam.sql.JDBCHelper;

public class Book {
	public final SmallBook book;
	public final String author,isbn,description,url;
	public final long created_on;
	
	public Book(SmallBook book, ResultSet rs) throws SQLException {
		this.book = book;
		this.author = rs.getString(AUTHOR);
		this.isbn = rs.getString(ISBN);
		this.description = rs.getString(DESCRIPTION);
		this.url = rs.getString(URL);
		this.created_on = rs.getLong(CREATED_ON);
	}
	public Book(DataInputStream dis, SmallBook book) throws IOException {
		int id = dis.readInt();
		if(id != book.id)
			throw new IllegalArgumentException(String.format("id (%s) != sm.id (%s)", id, book.id));
		this.author = NULL2(dis.readUTF());
		this.isbn = NULL2(dis.readUTF());
		this.description = NULL2(dis.readUTF());
		this.url = NULL2(dis.readUTF());
		this.created_on = dis.readLong();
		this.book = book;
	}
	private String NULL2(String s) {
		return s.isEmpty() ? null : s;
	}
	public void write(DataOutputStream dos ) throws IOException{
		dos.writeInt(this.book.id);
		dos.writeUTF(NULL(this.author));
		dos.writeUTF(NULL(this.isbn));
		dos.writeUTF(NULL(this.description));
		dos.writeUTF(NULL(this.url));
		dos.writeLong(this.created_on);
	}
	private String NULL(String s) {
		return s == null ? "" : s ;
	}
	public static String[] columns() {
		return new String[] {AUTHOR,ISBN,DESCRIPTION,URL,CREATED_ON};
	}
	
	public static final String FIND_BY_ID = JDBCHelper.selectSQL(BOOK_TABLE_NAME, columns()).append(" WHERE ").append(BOOK_ID).append('=').toString();
	public static Book getById(SmallBook book, BooksDB db) throws SQLException{
		return db.findFirst(FIND_BY_ID+book.id, rs -> new Book(book, rs));
	}
}
