package sam.book;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static sam.io.IOUtils.compactOrClear;
import static sam.io.IOUtils.ensureCleared;
import static sam.io.IOUtils.readIf;
import static sam.io.IOUtils.write;
import static sam.io.IOUtils.writeIf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import sam.books.BookStatus;
import sam.books.PathsImpl;
import sam.functions.IOExceptionConsumer;
import sam.io.BufferConsumer;
import sam.io.BufferSupplier;
import sam.io.serilizers.StringIOUtils;
import sam.io.serilizers.WriterImpl;
import sam.myutils.Checker;
import sam.nopkg.Resources;

@SuppressWarnings({ "unchecked", "rawtypes" })
class BookSerializer {
	SmallBook[] books;
	PathsImpl[] paths;
	int last_log_number = -1;
	long lastmodifiedtime;
	Map<Integer, List<String>> resources;

	boolean load(Path book_cache) throws IOException {
		if(Files.notExists(book_cache)) 
			return false;

		try(FileChannel source = FileChannel.open(book_cache, READ); 
				Resources r = Resources.get();) {
			ByteBuffer buffer = r.buffer();
			ensureCleared(buffer);
			source.read(buffer);
			buffer.flip();

			if(buffer.remaining() > Integer.BYTES) {
				lastmodifiedtime = buffer.getLong();
				last_log_number = buffer.getInt();
				TempSMB[] books = new TempSMB[buffer.getInt()];
				int[] paths = new int[buffer.getInt()];
				int[] sql_resources_id = new int[buffer.getInt()];
				int[] sql_resources_size = new int[sql_resources_id.length];

				BookStatus[] statuses = BookStatus.values();

				for (int i = 0; i < books.length; i++){
					readIf(buffer, source, TempSMB.BYTES);

					TempSMB t = new TempSMB();

					t.id = buffer.getInt();
					t.page_count = buffer.getInt();
					t.year = buffer.getInt();
					t.path_id = buffer.getInt();
					t.status = status(buffer.getInt(), statuses);

					books[i] = t;
				}

				for (int i = 0; i < paths.length; i++) {
					readIf(buffer, source, 4);
					paths[i] = buffer.getInt();
				}
				for (int i = 0; i < sql_resources_id.length; i++) {
					readIf(buffer, source, 8);
					sql_resources_id[i] = buffer.getInt();
					sql_resources_size[i] = buffer.getInt();
				}

				compactOrClear(buffer);

				SmallBook[] sms = new SmallBook[books.length];
				PathsImpl[] paths2 = new PathsImpl[paths.length];
				List[] sqllists = new List[sql_resources_id.length];
				
				for (int i = 0; i < sqllists.length; i++)
					sqllists[i] = new ArrayList<>(sql_resources_size[i]);

				IOExceptionConsumer<StringBuilder> collector = new IOExceptionConsumer<StringBuilder>() {
					final int size1 = books.length;
					final int size2 = size1 + paths.length;
					// final int size3 = size2 + sql_resources.length;

					int index = 0;
					int sql_index = 0;

					@Override
					public void accept(StringBuilder s) throws IOException {
						int n = -1;

						if(index < size2) {
							for (int i = 0; i < s.length(); i++) {
								if(s.charAt(i) == '\t') {
									n = i;
								}
							}
						}

						if(index < size1) 
							sms[index] = new SmallBook(books[index], s.substring(0, n), s.substring(n+1));
						else if(index < size2)
							paths2[index - size1] = new PathsImpl(paths[index - size1], s.substring(0, n), s.substring(n+1));
						else {
							List list = sqllists[sql_index];
							list.add(s.toString());
							if(sql_resources_size[sql_index] == list.size())
								sql_index++;
						}
						index++;
					}
				};

				StringIOUtils.collect0(BufferSupplier.of(source, buffer), '\n', collector, r.decoder(), r.chars(), r.sb());
				
				HashMap<Integer, List<String>> sqls = new HashMap<>();
				
				for (int i = 0; i < sqllists.length; i++) 
					sqls.put(sql_resources_id[i], sqllists[i]);

				this.books = sms;
				this.paths = paths2;
				this.resources = sqls;
				
				return true;
			}
		}
		return false;
	}
	
	private BookStatus status(int n, BookStatus[] statuses) {
		return n < 0 ? null : statuses[n];
	}
	void save(Path book_cache) throws IOException {
		Checker.requireNonNull("books, paths, resources", books, paths, resources);

		try(FileChannel source = FileChannel.open(book_cache, WRITE, TRUNCATE_EXISTING, CREATE); 
				Resources r = Resources.get();) {

			ByteBuffer buffer = r.buffer();
			ensureCleared(buffer);

			buffer
			.putLong(lastmodifiedtime)
			.putInt(last_log_number)
			.putInt(books.length)
			.putInt(paths.length)
			.putInt(resources.size());
			
			
			
			for (SmallBook t : books) {
				writeIf(buffer, source, TempSMB.BYTES);
				
				buffer.putInt(t.id);
				buffer.putInt(t.page_count);
				buffer.putInt(t.year);
				buffer.putInt(t.path_id);
				buffer.putInt(t.getStatus() == null ? -1 : t.getStatus().ordinal());
			}

			for (PathsImpl p : paths) {
				writeIf(buffer, source, 4);
				buffer.putInt(p.getPathId());	
			}
			
			Entry[] entries = resources.entrySet().toArray(new Entry[resources.size()]);
			for (Entry e : entries) {
				writeIf(buffer, source, 8);
				buffer.putInt((int) e.getKey()).putInt(((List) e.getValue()).size());
			}
			
			write(buffer, source, true);
			
			try(WriterImpl w = new WriterImpl(BufferConsumer.of(source, false), buffer, r.chars(), false, r.encoder())) {
				for (SmallBook t : books) {
					w.append(t.name).append('\t').append(t.filename).append('\n');
				}
				for (PathsImpl t : paths) {
					w.append(t.getPath()).append('\t').append(t.getMarker()).append('\n');
				}
				for (Entry e : entries) {
					for (String s : (List<String>)e.getValue()) {
						w.append(s).append('\n');
					}
				}	
			}
		}
	}
	
	public static void updateLastModified(Path book_cache, long lastModified) throws IOException {
		try(FileChannel f = FileChannel.open(book_cache, WRITE);) {
			f.write((ByteBuffer) ByteBuffer.allocate(8).putLong(lastModified).flip(), 0);
		}	
	}
}
