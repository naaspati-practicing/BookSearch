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
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;

import sam.books.BooksDB;
import sam.io.serilizers.DataReader;
import sam.io.serilizers.DataWriter;
import sam.nopkg.Resources;
import sam.sql.JDBCHelper;

public class Book {
    public final SmallBook book;
    public final String author,isbn,description,url;
    public final long created_on;

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
                Resources r = Resources.get();
                DataReader reader = new DataReader(fc, r.buffer());
                ) {
            reader.setChars(r.chars());
            reader.setStringBuilder(r.sb());

            int id = reader.readInt();

            if(id != book.id)
                throw new IllegalArgumentException(String.format("id (%s) != sm.id (%s)", id, book.id));

            this.book = book;
            this.created_on = reader.readLong();

            author = reader.readUTF(); 
            isbn = reader.readUTF();
            description = reader.readUTF();
            url = reader.readUTF();
        }
    }

    public void write(Path path) throws IOException{
        try(FileChannel fc = FileChannel.open(path, CREATE, TRUNCATE_EXISTING, WRITE);
                Resources r = Resources.get();
                DataWriter w = new DataWriter(fc, r.buffer());
                ) {
            
            w.writeInt(book.id);
            w.writeLong(this.created_on);

             w.writeUTF(author); 
             w.writeUTF(isbn);
             w.writeUTF(description);
             w.writeUTF(url);
        }
    }
    public static String[] columns() {
        return new String[] {AUTHOR,ISBN,DESCRIPTION,URL,CREATED_ON};
    }

    public static final String FIND_BY_ID = JDBCHelper.selectSQL(BOOK_TABLE_NAME, columns()).append(" WHERE ").append(BOOK_ID).append('=').toString();
    public static Book getById(SmallBook book, BooksDB db) throws SQLException{
        return db.findFirst(FIND_BY_ID+book.id, rs -> new Book(book, rs));
    }
}
