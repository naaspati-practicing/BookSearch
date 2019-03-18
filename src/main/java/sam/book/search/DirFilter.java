package sam.book.search;

import java.util.BitSet;
import java.util.Objects;
import java.util.function.Predicate;

import sam.book.SmallBook;

public class DirFilter implements Predicate<SmallBook> {
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
	public byte[] bytes() {
		return set.toByteArray();
	}
	@Override
	public boolean equals(Object obj) {
		return obj != null && obj instanceof DirFilter && Objects.equals(this.set, ((DirFilter) obj).set);
	}
	public BitSet actual() {
		return set;
	}
	
}
