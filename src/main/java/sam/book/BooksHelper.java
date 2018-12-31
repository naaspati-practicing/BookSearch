package sam.book;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static sam.books.BooksMeta.BOOK_ID;
import static sam.books.BooksMeta.BOOK_TABLE_NAME;
import static sam.books.BooksMeta.CHANGE_LOG_TABLE_NAME;
import static sam.books.BooksMeta.DML_TYPE;
import static sam.books.BooksMeta.ID;
import static sam.books.BooksMeta.LOG_NUMBER;
import static sam.books.BooksMeta.PATH_TABLE_NAME;
import static sam.books.BooksMeta.TABLENAME;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import sam.books.BookStatus;
import sam.books.BooksDB;
import sam.books.PathsImpl;
import sam.collection.IndexedMap;
import sam.collection.IntSet;
import sam.fx.alert.FxAlert;
import sam.fx.helpers.FxTextSearch;
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.FilesUtilsIO;
import sam.io.serilizers.LongSerializer;
import sam.io.serilizers.ObjectReader;
import sam.io.serilizers.ObjectWriter;
import sam.logging.MyLoggerFactory;
import sam.myutils.Checker;
import sam.sql.JDBCHelper;
import sam.sql.SqlFunction;

public class BooksHelper implements AutoCloseable {
	private static final Logger LOGGER = MyLoggerFactory.logger(BooksHelper.class);

	private final Path book_cache = Paths.get("books_cache.dat");
	private final Path sql_resources = Paths.get("sql-resources.dat");
	private Map<Integer, List<String>> resources;
	private boolean resources_modified;
	private boolean db_modified;
	private int last_log_number;

	private final Path cache_dir = Paths.get("full.books.cache");

	private SmallBook[] _books;
	private PathsImpl[] _paths;
	private final IndexedMap<SmallBook> books;
	private final IndexedMap<PathsImpl> paths;

	private final HashMap<Integer, Book> _loadedData  = new HashMap<>();
	private final HashMap<String, int[]> sqlFilters = new HashMap<>();
	private boolean modified = false;

	public BooksHelper() throws ClassNotFoundException, SQLException, URISyntaxException, IOException {
		load();
		this.books = new IndexedMap<>(_books, s -> s.id);
		this.paths = new IndexedMap<>(_paths, PathsImpl::getPathId);
		Files.createDirectories(cache_dir);
		
		if(resources == null)
			resources = new HashMap<>();
	}
	public IndexedMap<SmallBook> getBooks() {
		return books;
	}
	public SmallBook getSmallBook(int id) {
		return books.get(id);
	}
	public Book book(SmallBook n) {
		return _loadedData.computeIfAbsent(n.id, id -> newBook(n));
	}
	private Book newBook(SmallBook n) {
		try {
			Path p = cache_dir.resolve(String.valueOf(n.id));
			if(Files.exists(p)) {
				LOGGER.fine(() -> "CACHE LOADED: id="+n.id+", name="+n.name);
				return ObjectReader.read(p, dis -> new Book(dis, n));
			}

			Book book = Book.getById(n, db()); 
			ObjectWriter.write(p, book, Book::write);
			LOGGER.fine(() -> "SAVED CACHE id:"+book.book.id+", to:"+p);
			return book;
		} catch (SQLException | IOException e) {
			System.err.println(n);
			e.printStackTrace();
		}
		return null;
	}

	private BooksDB _db;
	private BooksDB db() throws SQLException {
		if(_db != null) return _db;
		StackTraceElement[] e = Thread.currentThread().getStackTrace();
		System.out.println("DB.init: "+e[2]+", "+e[3]);
		return _db = new BooksDB();
	}
	public Path getExpectedSubpath(SmallBook book) {
		String dir = dir(book);
		return Paths.get(dir, book.filename);
	}
	private String dir(SmallBook book) {
		return paths.get(book.path_id).getPath();
	}
	public Path getExpepectedFullPath(SmallBook book) {
		return BooksDB.ROOT.resolve(getExpectedSubpath(book));
	}
	public Path getFullPath(SmallBook book) {
		Path p2 = BooksDB.findBook(getExpepectedFullPath(book));
		return p2 != null ? p2 : getExpepectedFullPath(book);
	}
	private void load() throws SQLException, ClassNotFoundException, URISyntaxException, IOException {
		if(Files.notExists(book_cache)) {
			loadAll();
			return ;
		}
		try(InputStream is = Files.newInputStream(book_cache, READ);
				BufferedInputStream buffer = new BufferedInputStream(is, 4*1024);
				DataInputStream dis = new DataInputStream(buffer);
				) {
			last_log_number = dis.readInt();
			this._books = new SmallBook[dis.readInt()];
			this._paths = new PathsImpl[dis.readInt()];

			for (int i = 0; i < _books.length; i++)
				_books[i] = new SmallBook(dis);
			for (int i = 0; i < _paths.length; i++)
				_paths[i] = new PathsImpl(dis.readInt(), dis.readUTF(), dis.readUTF());

			update();
			this.resources = Files.notExists(sql_resources) ? new HashMap<>() : ObjectReader.read(sql_resources);
			this.resources.replaceAll((id, value) -> value == null ? Collections.emptyList() : value);
		} catch (IOException e) {
			e.printStackTrace();
			loadAll();
		}
	}
	private void save() throws IOException {
		try(OutputStream is = Files.newOutputStream(book_cache, WRITE, TRUNCATE_EXISTING, CREATE);
				BufferedOutputStream buffer = new BufferedOutputStream(is, 4*1024);
				DataOutputStream dis = new DataOutputStream(buffer);
				) {
			 dis.writeInt(last_log_number);
			 dis.writeInt(this._books.length);
			 dis.writeInt(this._paths.length);
			 
			 for (SmallBook s : _books)
				s.write(dis);
			 
			 for (PathsImpl p : _paths) {
				 dis.writeInt(p.getPathId());
				 dis.writeUTF(p.getPath());
				 dis.writeUTF(p.getMarker());
			}
		}
	}

	private final Path lastmodifiedtime = Paths.get("db_last_modified.long");

	private void update() throws SQLException, IOException {
		if(Files.notExists(lastmodifiedtime) || last_log_number == 0) {
			loadAll();
			return;
		}

		File dbfile = BooksDB.DB_PATH.toFile();
		if(LongSerializer.read(lastmodifiedtime) == dbfile.lastModified()) {
			System.out.println("UPDATE SKIPPED: LongSerializer.read(lastmodifiedtime.toPath()) == dbfile.lastModified()");
			return;
		}

		Files.deleteIfExists(sql_resources);
		System.out.println("deleted: "+sql_resources);

		Temp252 books = new Temp252();
		Temp252 paths = new Temp252();

		try(ResultSet rs = db().executeQuery(JDBCHelper.selectSQL(CHANGE_LOG_TABLE_NAME, "*").append(" WHERE ").append(LOG_NUMBER).append('>').append(last_log_number).append(';').toString())) {
			while(rs.next()) {
				last_log_number = Math.max(last_log_number, rs.getInt(LOG_NUMBER));
				Temp252 t;

				switch (rs.getString(TABLENAME)) {
					case BOOK_TABLE_NAME:
						t = books;
						break;
					case PATH_TABLE_NAME:
						t = paths;
						break;
					default:
						throw new IllegalArgumentException("unknown "+TABLENAME+": "+rs.getString(TABLENAME));
				}
				IntSet list;

				switch (rs.getString(DML_TYPE)) {
					case "INSERT":
						list = t.nnew;
						break;
					case "UPDATE":
						list = t.update;
						break;
					case "DELETE":
						list = t.delete;
						break;
					default:
						throw new IllegalArgumentException("unknown "+DML_TYPE+": "+rs.getString(DML_TYPE));
				}
				list.add(rs.getInt(ID));
			}
		}
		
		if(books.isEmpty() && paths.isEmpty()) {
			System.out.println("NO Changes found in change log");
			return;
		}
		 
		modified = true;
		
		_books = apply(_books, books, s -> s.id, SmallBook::new, BOOK_TABLE_NAME, ID, SmallBook.columns());
		
	}

	private <E> E[] apply(E[] array, Temp252 temp, ToIntFunction<E> idOf, SqlFunction<ResultSet, E> mapper, String tablename, String idColumn, String[] columnNames) throws SQLException {
		if(temp.isEmpty())
			return array;
		
		if(!temp.delete.isEmpty()) {
			for (int i = 0; i < array.length; i++) {
				E e = array[i];
				if(temp.delete.contains(idOf.applyAsInt(e))) {
					System.out.println("DELETE: "+e);
					array[i] = null;
				}
			}
		}
		
		if(!temp.update.isEmpty() || !temp.nnew.isEmpty()) {
			StringBuilder sb = JDBCHelper.selectSQL(tablename, columnNames)
					.append(" WHERE ")
					.append(idColumn).append(" IN(");

			temp.nnew.forEach(s -> sb.append(s).append(','));
			temp.update.forEach(s -> sb.append(s).append(','));

			sb.setLength(sb.length() - 1);
			sb.append(");");
			
			Map<Integer, E> map = db().collectToMap(sb.toString(), rs -> rs.getInt(idColumn), mapper);
			
			for (int i = 0; i < array.length; i++) {
				E old = array[i];
				if(old == null)
					continue;
				int id = idOf.applyAsInt(old);
				E nw = map.remove(id);
				
				if(nw != null) {
					System.out.println("UPDATE: "+old +" -> "+nw);
					array[i] = nw;
				}
			}
			
			if(!map.isEmpty()) {
				int size = array.length;
				array = Arrays.copyOf(array, array.length + map.size());
				for (E e : map.values()) {
					array[size++] = e;
					System.out.println("NEW: "+e);
				}
			}
		}
		
		int n = 0;
		for (E e : array) {
			if(e != null)
				array[n++] = e;
		}
		
		if(n == array.length)
			return array;
		
		System.out.println(tablename+" array resized: "+array.length +" -> "+ n);
		return Arrays.copyOf(array, n);
	}

	private class Temp252 {
		private final IntSet nnew = new IntSet();
		private final IntSet delete = new IntSet();
		private final IntSet update = new IntSet();
		
		public boolean isEmpty() {
			return nnew.isEmpty() && delete.isEmpty() && update.isEmpty();
		}
	} 

	private void deleteCache(SmallBook sm) {
		boolean b = cache_dir.resolve(Integer.toString(sm.id)).toFile().delete();
		LOGGER.fine(() -> "DELETE CACHE: "+sm.id+", deleted: "+b);
	}

	private void loadAll() throws SQLException, IOException {
		ArrayList<SmallBook> books = db().collectToList(JDBCHelper.selectSQL(BOOK_TABLE_NAME, SmallBook.columns()).toString(), SmallBook::new);
		ArrayList<PathsImpl> paths = db().collectToList("SELECT * FROM "+PATH_TABLE_NAME, PathsImpl::new);
		last_log_number = Optional.ofNullable(db().findFirst("SELECT max("+LOG_NUMBER+") FROM "+CHANGE_LOG_TABLE_NAME, rs -> rs.getInt(1))).orElse(0);
		
		System.out.println("DB loaded");
		FilesUtilsIO.deleteDir(cache_dir);
		modified = true;
		
		this._books = books.toArray(new SmallBook[books.size()]);
		this._paths = paths.toArray(new PathsImpl[paths.size()]);
	}

	@Override
	public void close() throws Exception {
		if(_db != null)
			_db.close();

		if(modified)
			save();

		if(resources_modified) {
			resources.replaceAll((id, value) -> Checker.isEmpty(value) ? null : value);
			ObjectWriter.write(sql_resources, resources);
			System.out.println("saved: "+sql_resources);
		}

		if(modified || db_modified)
			LongSerializer.write(BooksDB.DB_PATH.toFile().lastModified(), lastmodifiedtime);
	}
	public void changeStatus(List<SmallBook> books, BookStatus status) {
		try {
			if(books.size() == 1) {
				SmallBook b = books.get(0);
				db().changeBookStatus(Collections.singletonMap(b.id, getFullPath(b)), status);
			} else {
				db().changeBookStatus(books.stream().collect(Collectors.toMap(b -> b.id, b -> getFullPath(b))), status);
			}
			FxPopupShop.showHidePopup("status changed to: "+status, 1500);
		} catch (Exception e) {
			FxAlert.showErrorDialog(null, "failed to change status", e);
			return;
		}
		books.forEach(sm -> {
			deleteCache(sm);
			_loadedData.remove(sm.id);
			sm.setStatus(status);
		});
	}
	private static final String SQL_FILTER = "SELECT "+BOOK_ID+" FROM "+BOOK_TABLE_NAME+" WHERE "; 
	public Predicate<SmallBook> sqlFilter(String sql) throws SQLException {
		int[] array = sqlFilters.get(sql);
		if(array == null) {
			array = new int[books.size()];
			int n[] = {0};
			int[] ar = array;
			db().iterate(SQL_FILTER.concat(sql), rs -> ar[n[0]++] = rs.getInt(1));
			array = n[0] == 0 ? new int[0] : Arrays.copyOf(array, n[0]);
			Arrays.sort(array);
			sqlFilters.put(sql, array);
		}
		if(array.length == 0)
			return FxTextSearch.falseAll();

		int[] ar = array;
		return s -> Arrays.binarySearch(ar, s.id) >= 0;
	}
	
	public List<String> getResources(Book b) throws SQLException {
		if(b == null)
			return Collections.emptyList();

		List<String> list = resources.get(b.book.id);
		if(list != null)
			return list;

		list = db().collectToList("SELECT _data FROM Resources WHERE book_id = "+b.book.id, rs -> rs.getString(1));
		System.out.println("loaded sq-resource("+list.size()+"): book_id: "+b.book.id);
		list = list.isEmpty() ? Collections.emptyList() : list;

		resources.put(b.book.id, list);
		resources_modified = true;

		return list;
	}
	public void addResource(Book currentBook, List<String> result) throws SQLException {
		try(PreparedStatement ps = db().prepareStatement("INSERT INTO Resources VALUES(?,?)")) {
			for (String s : result) {
				ps.setInt(1, currentBook.book.id);
				ps.setString(2, s);
				ps.addBatch();
			}

			ps.executeBatch();
			db().commit();
			db_modified = true;
		}
	}
	public IndexedMap<PathsImpl> getPaths() {
		return paths;
	}
}
