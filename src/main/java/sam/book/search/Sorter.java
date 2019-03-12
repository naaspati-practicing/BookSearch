package sam.book.search;

import static java.util.Comparator.*;
import java.util.Comparator;

import sam.book.SmallBook;

public enum Sorter {
	ADDED(comparingInt((SmallBook s) -> s.id).reversed()), 
	YEAR((a,b) -> {
		if(a.year == b.year)
			return Integer.compare(b.id, a.id);
		return Integer.compare(b.year, a.year);
	}), 
	TITLE( comparing(s -> s.lowercaseName)),
	PAGE_COUNT( comparingInt(s -> s.page_count)),
	DEFAULT(null);
	
	public final Comparator<SmallBook> sorter;
	private Sorter(Comparator<SmallBook> sorter) {
		this.sorter = sorter;
	}
}
