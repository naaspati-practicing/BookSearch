package sam.book;
import static sam.books.BooksMeta.BOOK_ID;
import static sam.books.BooksMeta.FILE_NAME;
import static sam.books.BooksMeta.NAME;
import static sam.books.BooksMeta.PAGE_COUNT;
import static sam.books.BooksMeta.PATH_ID;
import static sam.books.BooksMeta.STATUS;
import static sam.books.BooksMeta.YEAR;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.sql.ResultSet;
import java.sql.SQLException;

import sam.books.BookStatus;

public class SmallBook {
    public final int id, page_count, year, path_id;
    public final String name, filename, lowercaseName;
    private BookStatus status;
    
    public static String[] columns() {
    	return new String[]{BOOK_ID, NAME,FILE_NAME, PATH_ID, PAGE_COUNT, YEAR,STATUS};
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
    
    static int readInt(ResultSet rs, String col) throws SQLException {
        try {
            return rs.getInt(col);
        } catch (NumberFormatException|NullPointerException e) {}
        return -1;
    }
    SmallBook(DataInputStream dis) throws IOException {
    	this.path_id = dis.readInt();
        this.id = dis.readInt();
        this.page_count = dis.readInt();
        this.year = dis.readInt();
        this.name = dis.readUTF();
        this.filename = dis.readUTF();
        this.lowercaseName = name.toLowerCase();
        this.status = BookStatus.valueOf(dis.readUTF());
    }
    void write(DataOutputStream dos) throws IOException {
    	dos.writeInt(this.path_id);
        dos.writeInt(this.id);
        dos.writeInt(this.page_count);
        dos.writeInt(this.year);
        dos.writeUTF(this.name);
        dos.writeUTF(this.filename);
        dos.writeUTF(this.status.toString());
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