package sam.book.search;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import sam.book.SmallBook;
import sam.books.BookStatus;
import sam.fx.textsearch.FxTextSearch;
import sam.myutils.System2;

public class Filters {
	private static final Logger LOGGER = LoggerFactory.getLogger(Filters.class);

	private Predicate<SmallBook> tr = FxTextSearch.trueAll();
	private static int _index = 0;
	private static final int CHOICE = _index++; 
	private static final int DIR_FILTER = _index++;
	private static final int SQL = _index++;
	private static final int SET = _index++;
	
	private static final int SIZE = _index;

	@SuppressWarnings("rawtypes")
	private Predicate[] preFilters = new Predicate[SIZE];

	private Comparator<SmallBook> currentComparator = Sorter.DEFAULT.sorter;
	private Status2 choice; 
	private DirFilter dir_filter;
	private BitSet sql;
	private Set<String> set;
	private String string;

	private final FxTextSearch<SmallBook> search = new FxTextSearch<>(s -> s.lowercaseName, Optional.ofNullable(System2.lookup("SEARCH_DELAY")).map(Integer::parseInt).orElse(500), true);

	public Filters() {
		search.disable();
	}
	public void setChoiceFilter(Status2 status) {
		this.choice = status;
		setPreFilter(CHOICE, choiceFilter(status));
	}
	private Predicate<SmallBook> choiceFilter(Status2 status) {
		if(status == null || status.status == null)
			return null;
		else {
			BookStatus sts =  status.status;
			return predicate(s -> s.getStatus() == sts);
		}
	}
	public void setDirFilter(DirFilter dirFilter) {
		this.dir_filter = dirFilter;
		setPreFilter(DIR_FILTER, bitsetFilter(dirFilter == null ? null : dirFilter.actual(), s -> s.path_id));
	}
	private Predicate<SmallBook> bitsetFilter(BitSet set, ToIntFunction<SmallBook> func) {
		if(set == null)
			return null;
		
		return s -> s != null && set.get(func.applyAsInt(s));
	}
	public DirFilter getDirFilter() {
		return dir_filter;
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

	private void setPreFilter(int type, Predicate<SmallBook> filter) {
		preFilters[type] = filter;
		search.set(preFilter(), null);
	}
	public void setStringFilter(String str) {
		this.string = str;

		if(set != null || preFilters[SET] != null) {
			preFilters[SET] = null;
			set = null;
			search.set(preFilter(), str);	
		} else
			search.set(str);
	}
	public void setStringFilter(Set<String> set) {
		this.set = set;
		setPreFilter(SET, predicate(s -> s != null && set.contains(s.filename)));
	}
	public void setSQLFilter(BitSet filter) {
		this.sql = filter;
		setPreFilter(SQL, bitsetFilter(filter, s -> s.id));
	}
	public void setFalse() {
		this.choice = null;
		this.dir_filter = null;
		this.sql = null;
		this.set = null;
		this.string = null;
		
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
	public void enable() {
		search.enable();
		if(string != null || Arrays.stream(preFilters).anyMatch(p -> p != null))
			search.set(preFilter(), string);
	}
	
	public void loadFilters(AppState f) {
		
		this.choice = f.choice;
		this.dir_filter = f.dir_filter == null ? null : new DirFilter(f.dir_filter);
		this.sql = f.sql;
		this.set = f.set;
		this.string = f.string;
		
		search.disable();
		
		preFilters[CHOICE] = choiceFilter(choice);
		preFilters[DIR_FILTER] = bitsetFilter(dir_filter == null ? null : dir_filter.actual(), s -> s.path_id);
		preFilters[SQL] = bitsetFilter(sql, s -> s.id);
		Set<String> set2 = set;
		preFilters[SET] = set == null ? null : predicate(s -> s != null && set2.contains(s.filename));

		Platform.runLater(() -> enable());
	}
	
	public void save(AppState f) throws IOException {
		f.choice = this.choice;
		f.dir_filter = this.dir_filter == null ? null : this.dir_filter.actual();
		f.sql = this.sql;
		f.set = this.set;
		f.string = this.string;
	}
	public String getSearchString() {
		return string;
	}
	public Status2 choice() {
		return choice;
	}
}
