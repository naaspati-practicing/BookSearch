package sam.book;
import static sam.books.BooksMeta.BOOK_ID;
import static sam.books.BooksMeta.BOOK_TABLE_NAME;
import static sam.books.BooksMeta.CHANGE_LOG_TABLE_NAME;
import static sam.books.BooksMeta.DML_TYPE;
import static sam.books.BooksMeta.ID;
import static sam.books.BooksMeta.LOG_NUMBER;
import static sam.books.BooksMeta.PATH_ID;
import static sam.books.BooksMeta.PATH_TABLE_NAME;
import static sam.books.BooksMeta.TABLENAME;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.books.BookStatus;
import sam.books.BooksDB;
import sam.books.PathsImpl;
import sam.collection.IndexedMap;
import sam.collection.IntSet;
import sam.fx.alert.FxAlert;
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.FilesUtilsIO;
import sam.sql.JDBCHelper;
import sam.sql.SqlFunction;

public class BooksHelper implements AutoCloseable {
	private static final Logger logger = LoggerFactory.getLogger(BooksHelper.class);
	private static final boolean DEBUG = logger.isDebugEnabled();
	
	private final Path book_cache = Paths.get("books_cache.dat");
	private Map<Integer, List<String>> resources;
	private boolean resources_modified;
	private boolean db_modified;
	private int last_log_number;
	private long lastmodifiedtime = -1;

	private final Path cache_dir = Paths.get("full.books.cache");

	private SmallBook[] _books;
	private PathsImpl[] _paths;
	private final IndexedMap<SmallBook> books;
	private final IndexedMap<PathsImpl> paths;

	private final HashMap<Integer, Book> _loadedData  = new HashMap<>();
	private final HashMap<String, BitSet> sqlFilters = new HashMap<>();
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
				logger.debug("CACHE LOADED: id={}, name={}", n.id, n.name);
				return new Book(p, n);
			}

			Book book = Book.getById(n, db()); 
			book.write(p);
			logger.debug( "SAVED CACHE id:{}, to:{}", book.book.id, p);
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
		if(DEBUG) {
			StackTraceElement[] e = Thread.currentThread().getStackTrace();
			logger.debug("DB.init: {}, {}", e[2], e[3]);
		}
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

	private void update() throws SQLException, IOException {
		if(lastmodifiedtime == -1 || last_log_number <= 0) {
			loadAll();
			return;
		}

		File dbfile = BooksDB.DB_PATH.toFile();
		if(lastmodifiedtime == dbfile.lastModified()) {
			logger.debug("UPDATE SKIPPED: lastmodifiedtime_old == dbfile.lastModified()");
			return;
		}

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
			logger.debug("NO Changes found in change log");
			return;
		}

		modified = true;

		_books = apply(_books, books, s -> s.id, SmallBook::new, BOOK_TABLE_NAME, ID, SmallBook.columns());
		_paths = apply(_paths, paths, PathsImpl::getPathId, PathsImpl::new, PATH_TABLE_NAME, PATH_ID, null);
	}

	private void loadAll() throws SQLException, IOException {
		LoadFromDb loader = new LoadFromDb();
		loader.loadAll(db());

		last_log_number = loader.last_log_number;
		this._books = loader.books;
		this._paths = loader.paths;

		logger.debug("DB loaded");
		FilesUtilsIO.deleteDir(cache_dir);
		modified = true;
	}
	
	private <E> E[] apply(E[] array, Temp252 temp, ToIntFunction<E> idOf, SqlFunction<ResultSet, E> mapper, String tablename, String idColumn, String[] columnNames) throws SQLException {
		if(temp.isEmpty())
			return array;

		if(!temp.delete.isEmpty()) {
			for (int i = 0; i < array.length; i++) {
				E e = array[i];
				if(temp.delete.contains(idOf.applyAsInt(e))) {
					logger.debug("DELETE: "+e);
					array[i] = null;
				}
			}
		}

		if(!temp.update.isEmpty() || !temp.nnew.isEmpty()) {
			StringBuilder sb = (columnNames == null ? new StringBuilder("SELECT * FROM ").append(tablename) : JDBCHelper.selectSQL(tablename, columnNames))
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
					logger.info("UPDATE: "+old +" -> "+nw);
					array[i] = nw;
				}
			}

			if(!map.isEmpty()) {
				int size = array.length;
				array = Arrays.copyOf(array, array.length + map.size());
				for (E e : map.values()) {
					array[size++] = e;
					logger.info("NEW: "+e);
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

		logger.debug(tablename+" array resized: "+array.length +" -> "+ n);
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
		logger.debug("DELETE CACHE: {}, deleted: {}",sm.id, b);
	}

	private void load() throws SQLException, IOException {
		BookSerializer serializer = new BookSerializer();
		try {
			if(serializer.load(book_cache)) {
				this._books = serializer.books;
				this._paths = serializer.paths;
				this.resources = serializer.resources;
				this.last_log_number = serializer.last_log_number;
				this.lastmodifiedtime = serializer.lastmodifiedtime;
				update();
				logger.debug("cache loaded: {}", book_cache);
			} else {
				loadAll();
			}
		} catch (Exception e) {
			e.printStackTrace();
			loadAll();
		}
	}

	@Override
	public void close() throws Exception {
		if(_db != null)
			_db.close();

		if(_db != null)
			_db.close();

		if(modified || resources_modified) {
			BookSerializer serializer = new BookSerializer();
			serializer.books = _books;
			serializer.paths = _paths;
			serializer.last_log_number = last_log_number;
			serializer.lastmodifiedtime = BooksDB.DB_PATH.toFile().lastModified();
			serializer.resources = resources;
			
			serializer.save(book_cache);
			logger.debug("cache saved: {}", book_cache);
		} else if(db_modified) {
			BookSerializer.updateLastModified(book_cache, BooksDB.DB_PATH.toFile().lastModified());
		}
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
	public BitSet sqlFilter(String sql) throws SQLException {
		BitSet set = sqlFilters.get(sql);

		if(set == null) {
			BitSet set2 = new BitSet(books.size());
			db().iterate(SQL_FILTER.concat(sql), rs -> set2.set(rs.getInt(1)));
			sqlFilters.put(sql, set2);
			set = set2;
		}

		return (BitSet) set.clone();
	}

	public List<String> getResources(Book b) throws SQLException {
		if(b == null)
			return Collections.emptyList();

		List<String> list = resources.get(b.book.id);
		if(list != null)
			return list;

		list = db().collectToList("SELECT _data FROM Resources WHERE book_id = "+b.book.id, rs -> rs.getString(1));
		logger.debug("loaded sq-resource({}): book_id: {}", list.size(), b.book.id);
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
	public PathsImpl getPath(int id) {
		return paths.get(id);
	}
}
