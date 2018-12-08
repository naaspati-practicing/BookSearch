package sam.book.search;

import java.util.Comparator;

import sam.book.SmallBook;

public enum Sorter {
	ADDED, YEAR, TITLE;

	public Comparator<SmallBook> sorter() {
		switch (this) {
			case ADDED: return Comparator.<SmallBook>comparingInt(s -> s.id).reversed();
			case TITLE: return Comparator.comparing(s -> s.lowercaseName);
			case YEAR: return (a,b) -> {
				if(a.year == b.year)
					return Integer.compare(b.id, a.id);
				return Integer.compare(b.year, a.year);
			};
		}
		return null;
	}
}
