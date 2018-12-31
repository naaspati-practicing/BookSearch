package sam.book.search;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;

import sam.book.Book;
import sam.book.SmallBook;
import sam.collection.IntList;
import sam.collection.IntSet;
import sam.io.serilizers.IntSerializer;

public class RecentsBookTab extends SmallBookTab implements Closeable {
	private IntList exists;
	private boolean modified;
	private Comparator<SmallBook> sorter;
	private Filters filter;

	private final Path cache_path = Paths.get("recents.dat") ;

	public void init(IntFunction<SmallBook> bookGetter) throws IOException {
		this.exists = Files.notExists(cache_path) ? new IntList() : new IntList(IntSerializer.readArray(cache_path));

		IntSet already = new IntSet();

		for (int i = exists.size() - 1; i >= 0; i--) {
			int id = exists.get(i);
			if(!already.contains(id)) {
				already.add(id);
				SmallBook s = bookGetter.apply(id);
				if(s == null) {
					System.out.println("no book found for: "+id);
					modified = true;
				} else
					allData.add(s);
			}
		}
	}
	public void add(Book s) {
		int id = s.book.id;
		exists.add(id);

		allData.remove(s.book);
		allData.add(s.book);

		modified = true;

		if(filter != null && !filter.applyFilter(s.book))
			return;

		if(sorter == null) {
			getSelectionModel().clearSelection();
			list.getItems().remove(s.book);
			list.getItems().add(0, s.book);
			getSelectionModel().select(0);
		} else {
			super.setSorter(sorter);
		}
	}

	@Override
	public void setSorter(Comparator<SmallBook> sorter) {
		this.sorter = sorter;
		super.setSorter(sorter);
	}
	public boolean isEmpty() {
		return allData.isEmpty();
	}
	@Override
	public void removeAll(List<SmallBook> books) {
		super.removeAll(books);
		if(books.isEmpty())
			return;
		
		if(books.size() == 1)
			exists.remove(books.get(0).id);
		else
			books.forEach(e -> exists.remove(e.id));
			
		modified = true;
	}
	@Override
	public void filter(Filters f) {
		super.filter(f);
		this.filter = f;
	}
	@Override
	public void close() throws IOException {
		if(!modified) return;
		
		if(allData.isEmpty())
			Files.deleteIfExists(cache_path);
		else {
			IntSerializer.write(exists.toArray(), cache_path);
			System.out.println("saved: "+cache_path);
		}
	}
}
