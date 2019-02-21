package sam.book.search;

import static sam.myutils.MyUtilsException.noError;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import sam.book.SmallBook;
import sam.books.BookStatus;
import sam.fx.helpers.FxTextSearch;
import sam.myutils.System2;

public class Filters {
	public static final Path SAVE_DIR = Optional.ofNullable(System2.lookup("saved_search_dir")).map(Paths::get).orElse(Paths.get("."));//.orElseGet(() -> noError(() -> Paths.get(ClassLoader.getSystemResource("saved_search").toURI())));

	static {
		noError(() -> {
			Files.createDirectories(SAVE_DIR);
			return null;
		});
	}
	private final FxTextSearch<SmallBook> search = new FxTextSearch<>(s -> s.lowercaseName, Optional.ofNullable(System2.lookup("SEARCH_DELAY")).map(Integer::parseInt).orElse(500), true);

	private Predicate<SmallBook> tr = FxTextSearch.trueAll();
	private static int _index = 0;
	private static final int CHOICE = _index++, DIR_FILTER = _index++, SQL = _index++, SET = _index++; 
	@SuppressWarnings("rawtypes")
	private Predicate[] preFilters = new Predicate[_index];  

	public void setChoiceFilter(Status2 status) {
		if(status == null || status.status == null)
			preFilters[CHOICE] = null;
		 else {
			BookStatus sts =  status.status;
			preFilters[CHOICE] = predicate(s -> s.getStatus() == sts);
		}
		search.set(preFilter());
	}
	public void setDirFilter(DirFilter dirFilter) {
		preFilters[DIR_FILTER] = dirFilter; 
		search.set(preFilter(), null);
	}
	public DirFilter getDirFilter() {
		return (DirFilter) preFilters[DIR_FILTER];
	}
	private Predicate<SmallBook> predicate(Predicate<SmallBook> s) {
		return s;
	}
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Predicate<SmallBook> preFilter() {
		Predicate<SmallBook> p = tr;
		for (Predicate s : preFilters){
			if(s != null && s != tr)
				p = p.and(s);
		}
		return p;
	}

	public void setStringFilter(String str) {
		if(preFilters[SET] != null) {
			preFilters[SET] = null;
			search.set(preFilter(), str);	
		} else
			search.set(str);
	}
	public void setStringFilter(Set<String> set) {
		preFilters[SET] = predicate(s -> set.contains(s.filename));
		search.set(preFilter(), null);
	}
	public void setSQLFilter(Predicate<SmallBook> predicate) {
		preFilters[SQL] = predicate;
		search.set(preFilter());
	}
	public void setFalse() {
		Arrays.fill(preFilters, null);
		search.set(FxTextSearch.falseAll(), null);
	}
	public Predicate<SmallBook> getFilter() {
		return search.getFilter();
	}
	public void setOnChange(Runnable runnable) {
		search.setOnChange(runnable);
	}
	public void setAllData(List<SmallBook> allData) {
		search.setAllData(allData);
	}
	public Collection<SmallBook> applyFilter(Collection<SmallBook> list) {
		return search.applyFilter(list);
	}
	public boolean applyFilter(SmallBook s) {
		return search.getFilter().test(s);
	}
}
