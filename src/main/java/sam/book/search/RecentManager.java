package sam.book.search;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import sam.book.Book;
import sam.book.SmallBook;
import sam.collection.Iterables;

public class RecentManager {
	private LinkedList<Integer> ids;
	private final Path path;
	private boolean modified;

	public RecentManager() throws URISyntaxException, IOException {
		path = Paths.get("recent.txt");
		ids = Files.notExists(path) ? new LinkedList<>() : Files.lines(path).filter(s -> !s.isEmpty()).map(Integer::parseInt).collect(Collectors.toCollection(LinkedList::new));
	}

	public List<SmallBook> get(List<SmallBook> list) {
		return ids.isEmpty() ? new ArrayList<>() : 
			list.stream()
			.filter(s -> ids.contains(s.id))
			.sorted(Comparator.comparing(s -> ids.indexOf(s.id)))
			.collect(Collectors.toList());
	}
	public boolean add(Book s) {
		if(ids.peekFirst() == s.book.id) return false;
		ids.remove(new Integer(s.book.id));
		ids.addFirst(s.book.id);
		modified = true;
		return true;
	}

	public void save() throws IOException {
		if(!modified) return;
		Files.write(path, Iterables.map(ids, String::valueOf), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}
	public boolean isEmpty() {
		return ids.isEmpty();
	}
	public void remove(List<SmallBook> books) {
		if(books.isEmpty())
			return;
		if(books.size() == 1)
			ids.remove(new Integer(books.get(0).id));
		else {
			int[] as = books.stream().mapToInt(b -> b.id).sorted().toArray();
			ids.removeIf(i -> Arrays.binarySearch(as, i) >= 0);			
		}
		modified = true; 
	} 
}
