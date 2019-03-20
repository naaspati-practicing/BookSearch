package sam.book;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.thedeanda.lorem.LoremIpsum;

import sam.books.BooksDB;
import sam.books.PathsImpl;

class BookSerializerTest {

	@Test
	void test() throws IOException, SQLException  {
		LoremIpsum lorem = new LoremIpsum();
		SmallBook[] books;
		PathsImpl[] paths;

		try(BooksDB db = new BooksDB();) {
			LoadFromDb d = new LoadFromDb();
			d.loadAll(db);
			books = d.books;
			paths = d.paths;
		}

		System.out.println(books.length);
		System.out.println(paths.length);

		SmallBook[] books2 = Arrays.copyOf(books, books.length);
		PathsImpl[] paths2 = Arrays.copyOf(paths, paths.length);;

		Random r = new Random();

		BookSerializer expected = new BookSerializer();
		expected.books = books;
		expected.paths = paths;
		expected.last_log_number = r.nextInt();
		expected.lastmodifiedtime = r.nextLong();
		expected.resources = new HashMap<>();

		Path p = Files.createTempFile(null, null);
		try {
			test(expected, p);
			
			for (int i = 0; i < 10; i++) 
				expected.resources.put(r.nextInt(), Stream.generate(() -> lorem.getWords(2,4)).limit(r.nextInt(20)).collect(Collectors.toList()));
			
			test(expected, p);
			
			expected.lastmodifiedtime = r.nextLong();
			BookSerializer.updateLastModified(p, expected.lastmodifiedtime);
			
			readtest(p, expected);
			
			assertSame(expected.books, books);
			assertSame(expected.paths, paths);
			assertArrayEquals(expected.books, books2);
			assertArrayEquals(expected.paths, paths2);			
		} finally {
			Files.deleteIfExists(p);
		}
	}

	private void test(BookSerializer serializer, Path p) throws IOException {
		serializer.save(p);
		readtest(p, serializer);
	}

	private void readtest(Path p, BookSerializer expected) throws IOException {
		BookSerializer bs2 = new BookSerializer();
		bs2.load(p);

		assertEquals(expected.books.length, bs2.books.length);

		for (int i = 0; i < expected.books.length; i++) {
			SmallBook e = expected.books[i];
			SmallBook a = bs2.books[i];

			assertNotSame(e, a);
			assertEquals(e.id, a.id);
			assertEquals(e.page_count, a.page_count);
			assertEquals(e.year, a.year);
			assertEquals(e.path_id, a.path_id);
			assertEquals(e.name, a.name);
			assertEquals(e.filename, a.filename);
			assertEquals(e.getStatus(), a.getStatus());
		}

		assertEquals(expected.paths.length, bs2.paths.length);

		for (int i = 0; i < expected.paths.length; i++) {
			PathsImpl e = expected.paths[i];
			PathsImpl a = bs2.paths[i];

			assertNotSame(e, a);
			assertEquals(e.getPathId(), a.getPathId());
			assertEquals(e.getPath(), a.getPath());
			assertEquals(e.getMarker(), a.getMarker());
		}
		
		assertNotSame(expected, bs2);
		assertEquals(expected.last_log_number, bs2.last_log_number);
		assertEquals(expected.lastmodifiedtime, bs2.lastmodifiedtime);
		assertEquals(expected.resources.size(), bs2.resources.size());
		assertNotSame(expected.resources, bs2.resources);
		assertEquals(expected.resources, bs2.resources);
		
	}
}
