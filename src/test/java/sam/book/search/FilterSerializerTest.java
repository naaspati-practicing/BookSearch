package sam.book.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.thedeanda.lorem.LoremIpsum;

import sam.myutils.Checker;

class FilterSerializerTest {

	@Test
	void test() throws IOException {
		try {
			p = Files.createTempFile(null, null);
			System.out.println(p);

			for (int i = 0; i < 1000; i++) {
				test0();
			}
		} finally {
			// TODO Files.deleteIfExists(p);
		}
	}

	Path p;
	final Random r = new Random();
	final LoremIpsum lorem = new LoremIpsum();
	final Status2[] choices = Status2.values();

	void test0() throws IOException {
		AppState f = new AppState();

		int n = r.nextInt(choices.length + 1);
		f.choice = n == choices.length ? null : choices[n];
		f.dir_filter = bitset();
		f.sql = bitset();
		f.set = r.nextBoolean() ? null : Stream.generate(() -> lorem.getTitle(3, 5)).limit(r.nextInt(20)).collect(Collectors.toSet());
		if(Checker.isEmpty(f.set))
			f.set = null;
		f.string = r.nextBoolean() ? null : lorem.getWords(3, 7);

		f.write(p);
		System.out.println(f);

		AppState fr = new AppState();
		fr.read(p);

		check(f.choice, fr.choice);
		check(f.dir_filter, fr.dir_filter);
		check(f.sql, fr.sql);
		check(f.set, fr.set);
		check(f.string, fr.string);
	}

	private BitSet bitset() {
		if(r.nextBoolean()) {
			return null;
		} else  {
			byte[] bytes = new byte[r.nextInt(1000)];
			r.nextBytes(bytes);
			return  BitSet.valueOf(bytes);
		}
	}

	private void check(Object expected, Object actual) {
		if(expected != null && expected.getClass() != Status2.class)
			assertNotSame(expected, actual);
		assertEquals(expected, actual);
	}
}
