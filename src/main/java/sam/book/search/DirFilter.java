package sam.book.search;

import java.util.BitSet;
import java.util.function.Predicate;

import sam.book.SmallBook;

public class DirFilter extends BitSet implements Predicate<SmallBook> {
	private static final long serialVersionUID = -7651738732807675513L;
	private BitSet set;

	public DirFilter(BitSet filter) {
		this.set = filter;
	}
	@Override
	public boolean test(SmallBook t) {
		return set == null ? true : set.get(t.path_id);
	}
	public BitSet copy() {
		return set == null ? null : (BitSet) set.clone();
	}
	
}
