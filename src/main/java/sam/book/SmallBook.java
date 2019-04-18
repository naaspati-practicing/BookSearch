package sam.book;
import static sam.books.BooksMeta.BOOK_ID;
import static sam.books.BooksMeta.FILE_NAME;
import static sam.books.BooksMeta.NAME;
import static sam.books.BooksMeta.PAGE_COUNT;
import static sam.books.BooksMeta.PATH_ID;
import static sam.books.BooksMeta.STATUS;
import static sam.books.BooksMeta.YEAR;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

import sam.books.BookStatus;
import sam.io.serilizers.DataReader;
import sam.io.serilizers.DataWriter;

public class SmallBook {
	public final int id, page_count, year, path_id;
    public final String name, filename, lowercaseName;
    private BookStatus status;
    
    public static String[] columns() {
    	return new String[]{BOOK_ID, NAME,FILE_NAME, PATH_ID, PAGE_COUNT, YEAR,STATUS};
    }
    
    public SmallBook(int id, int page_count, int year, int path_id, String name, String filename, BookStatus status) {
		this.id = id;
		this.page_count = page_count;
		this.year = year;
		this.path_id = path_id;
		this.name = name;
		this.filename = filename;
		this.status = status;
		this.lowercaseName = name.toLowerCase();
	}

	SmallBook(ResultSet rs) throws SQLException {
        this.id = rs.getInt(BOOK_ID);
        this.name = rs.getString(NAME);
        this.filename = rs.getString(FILE_NAME);
        this.page_count = readInt(rs, PAGE_COUNT);
        this.year = readInt(rs, YEAR);
        this.status = BookStatus.valueOf(rs.getString(STATUS));
        this.path_id = rs.getInt(PATH_ID);
        this.lowercaseName = name.toLowerCase();
    }
    
	public SmallBook(DataReader reader) throws IOException {
		this.id = reader.readInt();
		this.page_count = reader.readInt();
		this.year = reader.readInt();
		this.path_id = reader.readInt();
		this.status = status(reader.readInt());
		
		this.name = reader.readUTF();
		this.filename = reader.readUTF();
		this.lowercaseName = name.toLowerCase();
	}
	public void write(DataWriter w) throws IOException {
	     w.writeInt(this.id);
         w.writeInt(this.page_count);
         w.writeInt(this.year);
         w.writeInt(this.path_id);
         w.writeInt(this.status == null ? -1 : this.status.ordinal());
        
         w.writeUTF(this.name);
         w.writeUTF(this.filename);
	}
	
	private static final BookStatus[] statuses = BookStatus.values();
	
	private BookStatus status(int n) {
        return n < 0 ? null : statuses[n];
    }

    static int readInt(ResultSet rs, String col) throws SQLException {
        try {
            return rs.getInt(col);
        } catch (NumberFormatException|NullPointerException e) {}
        return -1;
    }
    public String name() {
		return name;
	}
    public String filename() {
		return filename;
	}
    public String lowercaseName() {
		return lowercaseName;
	}
    
    public BookStatus getStatus() {
		return status;
	}
    public void setStatus(BookStatus status) {
		this.status = status;
	}
    String tostring; 
    @Override
    public String toString() {
        if(tostring == null) {
            tostring = name +"\nid: "+id+
                    "  | pages: "+(page_count == -1 ? "--" : page_count)+
                    "  | year: "+ (year == -1 ? "--" : year);
        }
        return tostring;
    }
}