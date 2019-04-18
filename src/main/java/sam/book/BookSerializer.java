package sam.book;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import sam.books.PathsImpl;
import sam.io.serilizers.DataReader;
import sam.io.serilizers.DataWriter;
import sam.myutils.Checker;
import sam.nopkg.Resources;

class BookSerializer {
    
	SmallBook[] books;
	PathsImpl[] paths;
	int last_log_number = -1;
	long lastmodifiedtime;

	boolean load(Path book_cache) throws IOException {
		if(Files.notExists(book_cache)) 
			return false;

		try(FileChannel fc = FileChannel.open(book_cache, READ); 
				Resources r = Resources.get();
		        DataReader reader = new DataReader(fc, r.buffer());
		        ) {
		    
		    reader.setChars(r.chars());
		    reader.setStringBuilder(r.sb());
		    
		    lastmodifiedtime = reader.readLong();
		    last_log_number = reader.readInt();
		    books = new SmallBook[reader.readInt()];
		    paths = new PathsImpl[reader.readInt()];
		    
		    for (int i = 0; i < books.length; i++)
		        books[i] = new SmallBook(reader);
		    
		    for (int i = 0; i < paths.length; i++)
		        paths[i] = new PathsImpl(reader.readInt(), reader.readUTF(), reader.readUTF());
		}
		return true;
	}
	
	void save(Path book_cache) throws IOException {
		Checker.requireNonNull("books, paths", books, paths);

		try(FileChannel source = FileChannel.open(book_cache, WRITE, TRUNCATE_EXISTING, CREATE); 
				Resources r = Resources.get();
		        DataWriter w = new DataWriter(source, r.buffer())) {
		    
		     w.writeLong(lastmodifiedtime);
             w.writeInt(last_log_number);
             w.writeInt(books.length);
             w.writeInt(paths.length);
             
             for (SmallBook b : books) 
                b.write(w);
             
             for (PathsImpl p : paths) {
                 w.writeInt(p.getPathId());
                 w.writeUTF(p.getPath());
                 w.writeUTF(p.getMarker());
             }
		}
	}
	
	public static void updateLastModified(Path book_cache, long lastModified) throws IOException {
		try(FileChannel f = FileChannel.open(book_cache, WRITE);) {
			f.write((ByteBuffer) ByteBuffer.allocate(8).putLong(lastModified).flip(), 0);
		}	
	}
}
