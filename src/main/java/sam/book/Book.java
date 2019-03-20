package sam.book;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static sam.books.BooksMeta.AUTHOR;
import static sam.books.BooksMeta.BOOK_ID;
import static sam.books.BooksMeta.BOOK_TABLE_NAME;
import static sam.books.BooksMeta.CREATED_ON;
import static sam.books.BooksMeta.DESCRIPTION;
import static sam.books.BooksMeta.ISBN;
import static sam.books.BooksMeta.URL;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import sam.books.BooksDB;
import sam.functions.IOExceptionConsumer;
import sam.io.BufferConsumer;
import sam.io.BufferSupplier;
import sam.io.IOUtils;
import sam.io.serilizers.StringIOUtils;
import sam.io.serilizers.WriterImpl;
import sam.myutils.Checker;
import sam.nopkg.Resources;
import sam.sql.JDBCHelper;

public class Book {
	public final SmallBook book;
	public final String author,isbn,description,url;
	public final long created_on;
	
	private static int _n = 0; 
	private static final int AUTHOR_N = _n++;
	private static final int ISBN_N = _n++;
	private static final int DESCRIPTION_N = _n++;
	private static final int URL_N = _n++;
	
	private static final int[] len = new int[_n];
	private static final String[] strings = new String[_n];
	
	public Book(SmallBook book, String author, String isbn, String description, String url, long created_on) {
		this.book = book;
		this.author = author;
		this.isbn = isbn;
		this.description = description;
		this.url = url;
		this.created_on = created_on;
	}
	public Book(SmallBook book, ResultSet rs) throws SQLException {
		this.book = book;
		this.author = rs.getString(AUTHOR);
		this.isbn = rs.getString(ISBN);
		this.description = rs.getString(DESCRIPTION);
		this.url = rs.getString(URL);
		this.created_on = rs.getLong(CREATED_ON);
	}
	public Book(Path path, SmallBook book) throws IOException {
		try(FileChannel fc = FileChannel.open(path, READ);
				Resources r = Resources.get();) {
			
			ByteBuffer buffer = r.buffer();
			int n = fc.read(buffer);
			buffer.flip();
			
			if(n < 4)
				throw new IOException("empty file: "+path);
			
			int id = buffer.getInt();
			if(id != book.id)
				throw new IllegalArgumentException(String.format("id (%s) != sm.id (%s)", id, book.id));

			this.book = book;
			this.created_on = buffer.getLong();
			
			int sum = 0;
			for (int i = 0; i < len.length; i++) {
				IOUtils.readIf(buffer, fc, 4);
				len[i] = buffer.getInt();
				sum += len[i];
			}
			
			if(sum != 0) {
				StringBuilder sb = r.sb();
				IOUtils.compactOrClear(buffer);
				
				int[] k = {0}; 
				IOExceptionConsumer<CharBuffer> consumer = new IOExceptionConsumer<CharBuffer>() {
					int n = 0;
					@Override
					public void accept(CharBuffer e) throws IOException {
						while(e.hasRemaining()) {
							if(len[n] == sb.length()) {
								strings[n] = sb.length() == 0 ? null : sb.toString();
								sb.setLength(0);
								n++;
							} else {
								sb.append(e.get());
							}
						}
						
						k[0] = n;
						e.clear();
					}
				};
				StringIOUtils.read(BufferSupplier.of(fc, buffer), consumer, r.decoder(), r.chars());
				
				if(sb.length() != 0)
					strings[k[0]] = sb.toString();
					
			}
			
			this.author = strings[AUTHOR_N];
			this.isbn = strings[ISBN_N];
			this.description = strings[DESCRIPTION_N];
			this.url = strings[URL_N];
			
			Arrays.fill(len, 0);
			Arrays.fill(strings, null);
		}
	}
	
	public void write(Path path) throws IOException{
		try(FileChannel fc = FileChannel.open(path, CREATE, TRUNCATE_EXISTING, WRITE);
				Resources r = Resources.get();) {
			
			ByteBuffer buffer = r.buffer();
			buffer
			.putInt(this.book.id)
			.putLong(this.created_on);
			
			strings[AUTHOR_N] = this.author;
			strings[ISBN_N] = this.isbn;
			strings[DESCRIPTION_N] = this.description;
			strings[URL_N] = this.url;
			
			len[AUTHOR_N]  = len(this.author);
			len[ISBN_N]  = len(this.isbn);
			len[DESCRIPTION_N]  = len(this.description);
			len[URL_N]  = len(this.url);
			
			int sum = 0;
			for (int i = 0; i < len.length; i++) {
				sum += len[i];
				buffer.putInt(len[i]);
			}
			
			IOUtils.write(buffer, fc, true);
			
			if(sum != 0) {
				try(WriterImpl w = new WriterImpl(BufferConsumer.of(fc, false), buffer, r.chars(), false, r.encoder())) {
					for (String s : strings) {
						if(Checker.isNotEmpty(s))
							w.write(s);
					}
				}	
			}
			
			Arrays.fill(len, 0);
			Arrays.fill(strings, null);
		}
	}
	private int len(String s) {
		return s == null ? 0 : s.length();
	}
	public static String[] columns() {
		return new String[] {AUTHOR,ISBN,DESCRIPTION,URL,CREATED_ON};
	}
	
	public static final String FIND_BY_ID = JDBCHelper.selectSQL(BOOK_TABLE_NAME, columns()).append(" WHERE ").append(BOOK_ID).append('=').toString();
	public static Book getById(SmallBook book, BooksDB db) throws SQLException{
		return db.findFirst(FIND_BY_ID+book.id, rs -> new Book(book, rs));
	}
}
