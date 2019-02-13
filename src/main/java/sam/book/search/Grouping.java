package sam.book.search;

import java.util.function.Function;

import sam.book.SmallBook;

@SuppressWarnings("rawtypes")
public enum Grouping {
	YEAR(s -> s.year), 
	FIRST_LETTER(s -> Character.toUpperCase(s.name.charAt(0))),
	PATH_ID(s -> s.path_id);
	
	public final Function<SmallBook, Comparable> mapper;
	
	private Grouping(Function<SmallBook, Comparable> mapper) {
		this.mapper = mapper;
	}

}
