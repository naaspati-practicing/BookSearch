package sam.book.search;

import java.util.Comparator;

import sam.book.SmallBook;

public enum Sorter {
	ADDED(Comparator.<SmallBook>comparingInt(s -> s.id).reversed()), 
	YEAR((a,b) -> {
		if(a.year == b.year)
			return Integer.compare(b.id, a.id);
		return Integer.compare(b.year, a.year);
	}), 
	TITLE( Comparator.comparing(s -> s.lowercaseName)), 
	DEFAULT(null);
	
	public final Comparator<SmallBook> sorter;
	private Sorter(Comparator<SmallBook> sorter) {
		this.sorter = sorter;
	}
}
