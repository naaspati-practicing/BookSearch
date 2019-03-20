package sam.book.search;

import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;

import java.util.Comparator;

import sam.book.SmallBook;

public enum Sorter {
	ADDED(comparingInt(s -> s.id), true), 
	YEAR((a,b) -> {
		if(a.year == b.year)
			return Integer.compare(b.id, a.id);
		return Integer.compare(b.year, a.year);
	}), 
	TITLE( comparing(s -> s.lowercaseName)),
	PAGE_COUNT(comparingInt(s -> s.page_count)),
	DEFAULT(comparingInt(s -> s.id), true);
	
	public final Comparator<SmallBook> sorter;
	private Sorter(Comparator<SmallBook> sorter) {
		this(sorter, false);
	}
	private Sorter(Comparator<SmallBook> sorter, boolean reverse) {
		this.sorter = reverse ? sorter.reversed() : sorter;
	}
}
