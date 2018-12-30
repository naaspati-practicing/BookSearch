package sam.book;

import static sam.books.BooksMeta.BOOK_ID;
import static sam.books.BooksMeta.BOOK_TABLE_NAME;
import static sam.books.BooksMeta.CHANGE_LOG_TABLE_NAME;
import static sam.books.BooksMeta.LOG_NUMBER;
import static sam.books.BooksMeta.PATH_ID;
import static sam.books.BooksMeta.PATH_TABLE_NAME;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import sam.books.BookStatus;
import sam.books.BooksDB;
import sam.books.ChangeLog;
import sam.books.PathsImpl;
import sam.collection.IntSet;
import sam.fx.alert.FxAlert;
import sam.fx.helpers.FxTextSearch;
import sam.fx.popup.FxPopupShop;
import sam.io.serilizers.IntSerializer;
import sam.io.serilizers.LongSerializer;
import sam.io.serilizers.ObjectReader;
import sam.io.serilizers.ObjectWriter;
import sam.logging.MyLoggerFactory;
import sam.myutils.Checker;
import sam.sql.JDBCHelper;
import sam.sql.SqlConsumer;

public class BooksHelper implements AutoCloseable {
	private static final Logger LOGGER = MyLoggerFactory.logger(BooksHelper.class);

	private final Path book_cache = Paths.get("books-cache.dat");
	private final Path paths_cache = Paths.get("paths-cache.dat");
	private final Path sql_resources = Paths.get("sql-resources.dat");
	private final Path paths_cache_size = Paths.get("paths-cache-size.dat");
	private final Path last_log_number = Paths.get("last_"+LOG_NUMBER+".int");
	private Map<Integer, List<String>> resources;
	private boolean resources_modified;
	private boolean db_modified;
	
	private final Path cache_dir = Paths.get("full.books.cache");
	
	private List<SmallBook> books;
	private PathsImpl[] paths;
	private final List<SmallBook> unmodif;
	private final HashMap<Integer, Book> _loadedData  = new HashMap<>();
	private final HashMap<String, int[]> sqlFilters = new HashMap<>();
	private boolean modified = false;

	public BooksHelper() throws ClassNotFoundException, SQLException, URISyntaxException, IOException {
		load();
		Files.createDirectories(cache_dir);
		unmodif = Collections.unmodifiableList(books);
	}
	public List<SmallBook> getBooks() {
		return unmodif;
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
		return paths[book.path_id].getPath();
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
		try {
			this.books = ObjectReader.readList(book_cache, SmallBook::new);
			this.paths = new PathsImpl[IntSerializer.read(paths_cache_size)];
			ObjectReader.iterate(paths_cache, dis -> {
				PathsImpl impl = new PathsImpl(dis.readInt(), dis.readUTF(), dis.readUTF());
				this.paths[impl.getPathId()] = impl;
			});
			update();
			this.resources = Files.notExists(sql_resources) ? new HashMap<>() : ObjectReader.read(sql_resources);
			this.resources.replaceAll((id, value) -> value == null ? Collections.emptyList() : value);
		} catch (IOException e) {
			e.printStackTrace();
			loadAll();
		}
	}
	private void save() throws IOException {
		ObjectWriter.writeList(book_cache, books, SmallBook::write);
		IntSerializer.write(paths.length, paths_cache_size);
		List<PathsImpl> paths2 = Arrays.stream(paths).filter(Objects::nonNull).collect(Collectors.toList());
		ObjectWriter.writeList(paths_cache, paths2, (p, dos) -> {
			dos.writeInt(p.getPathId());
			dos.writeUTF(p.getPath());
			dos.writeUTF(p.getMarker());
		});
	}
	
	private final Path lastmodifiedtime = Paths.get("db_last_modified.long");

	private void update() throws SQLException, IOException {
		if(Files.notExists(lastmodifiedtime) || Files.notExists(last_log_number)) {
			loadAll();
			return;
		}
		File dbfile = BooksDB.DB_PATH.toFile();
		if(LongSerializer.read(lastmodifiedtime) == dbfile.lastModified()) {
			LOGGER.fine(() -> "UPDATE SKIPPED: LongSerializer.read(lastmodifiedtime.toPath()) == dbfile.lastModified()");
			return;
		}
		
		Files.deleteIfExists(sql_resources);

		List<ChangeLog> ui = ChangeLog.getAllAfter(IntSerializer.read(last_log_number), db(), false);
		System.out.println("CACHE loaded");
		if(ui.isEmpty()) return;

		Map<String, List<ChangeLog>> map = ui.stream().collect(Collectors.groupingBy(t -> t.table_name));

		IntSerializer.write(ui.stream().mapToInt(u -> u.log_number).max().getAsInt(), last_log_number);
		modified = true;
		
		List<ChangeLog> list = map.get(PATH_TABLE_NAME);
		if(Checker.isNotEmpty(list)) {
			int max = list.stream().mapToInt(u -> u.id).max().getAsInt();
			if(max >= paths.length)
				paths = Arrays.copyOf(paths, max+1);

			int[] delete = modify(list, SELECT_ALL_PATHS, PATH_ID, rs -> {
				PathsImpl p = new PathsImpl(rs);

				if(paths[p.getPathId()] != null)
					System.out.println("UPDATED: "+p);
				else
					System.out.println("NEW: "+p);
				paths[p.getPathId()] = p;

			});

			if(delete != null && delete.length != 0) {
				for (int j : delete) {
					if(j < paths.length && paths[j] != null) {
						System.out.println("DELETE: "+paths[j]);
						paths[j] = null;
					}
				}
			}
		}

		list = map.get(BOOK_TABLE_NAME);

		if(Checker.isNotEmpty(list)) {
			Map<Integer, SmallBook> books = new HashMap<>();
			int[] delete = modify(list, SELECT_ALL_SMALL_BOOKS, BOOK_ID, rs -> {
				SmallBook b = new SmallBook(rs);
				books.put(b.id, b);
			});
			
			if(!books.isEmpty()) {
				this.books.replaceAll(sm -> {
					SmallBook s = books.remove(sm.id);
					if(s != null) {
						deleteCache(sm);
						System.out.println("UPDATED: SmallBook[id: "+s.id+", name: "+s.name+"]");
						return s;
					}
					return sm;
				});
				if(!books.isEmpty()) {
					this.books.addAll(books.values());
					books.forEach((m,s) -> {
						System.out.println("NEW: SmallBook[id: "+s.id+", name: "+s.name+"]");
						deleteCache(s);
					});
				}
			}
			if(delete != null && delete.length != 0){
				Arrays.sort(delete);
				this.books.removeIf(sm -> {
					if(Arrays.binarySearch(delete, sm.id) >= 0) {
						System.out.println("REMOVE: SmallBook[id: "+sm.id+", name: "+sm.name+"]");
						deleteCache(sm);
						return  true;
					}
					return false;
				});
			}
		}
	}

	private void deleteCache(SmallBook sm) {
		boolean b = cache_dir.resolve(Integer.toString(sm.id)).toFile().delete();
		LOGGER.fine(() -> "DELETE CACHE: "+sm.id+", deleted: "+b);
	}
	/**
	 * 
	 * @param list
	 * @param selectAll
	 * @param column
	 * @param setter
	 * @param deleter
	 * @return ids to be deleted;
	 * @throws SQLException
	 */
	private <E> int[] modify(List<ChangeLog> list, String selectAll, String column, SqlConsumer<ResultSet> setter) throws SQLException {
		int[] array = list.stream().mapToInt(u -> u.id).distinct().toArray();
		Arrays.sort(array);

		StringBuilder sb = new StringBuilder(100)
				.append(selectAll)
				.append(" WHERE ")
				.append(column).append(" IN(");

		for (int s : array)
			sb.append(s).append(',');

		sb.setLength(sb.length() - 1);
		sb.append(')');

		IntSet set = new IntSet();

		db().iterate(sb.toString(), rs -> {
			set.add(rs.getInt(column));
			setter.accept(rs);
		});
		if(array.length == set.size())
			return null;
		return Arrays.stream(array)
				.filter(s -> !set.contains(s))
				.toArray();
	}

	public static final String SELECT_ALL_SMALL_BOOKS = JDBCHelper.selectSQL(BOOK_TABLE_NAME, SmallBook.columns()).toString();
	public static final String SELECT_ALL_PATHS = "SELECT * FROM "+PATH_TABLE_NAME;

	private void loadAll() throws SQLException, IOException {
		books = db().collectToList(SELECT_ALL_SMALL_BOOKS, SmallBook::new);
		paths = new PathsImpl[db().getSequnceValue(PATH_TABLE_NAME)+1];
		
		db().iterate(SELECT_ALL_PATHS, rs -> {
			PathsImpl impl = new PathsImpl(rs);
			paths[impl.getPathId()] = impl;
		});
		IntSerializer.write(db().executeQuery("SELECT max("+LOG_NUMBER+") FROM "+CHANGE_LOG_TABLE_NAME, rs -> rs.next() ? OptionalInt.of(rs.getInt(1)) : OptionalInt.empty()).orElse(-1), last_log_number);
		System.out.println("DB loaded");
		File file = cache_dir.toFile();
		if(file.exists()) {
			for (String s : file.list()) {
				new File(file, s).delete();
				LOGGER.fine(() -> "DELETE CACHE: "+s);
			}
		}
		modified = true;
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
	public Stream<PathsImpl> paths() {
		return Arrays.stream(paths).filter(Objects::nonNull);
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
}
