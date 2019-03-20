package sam.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.thedeanda.lorem.LoremIpsum;

import sam.books.BookStatus;

class BookTest {

	@Test
	void test() throws IOException {
		LoremIpsum lorem = new LoremIpsum();
		Random r = new Random();
		SmallBook sm = new SmallBook(r.nextInt(), r.nextInt(), r.nextInt(), r.nextInt(), lorem.getEmail(), lorem.getCountry(), BookStatus.READ);
		
		Path p = Paths.get("D:\\Downloads\\a.txt");

		for (int i = 0; i < 1000; i++) {
			Book write = new Book(sm, 
					(r.nextBoolean() ? null : lorem.getFirstName()),
					(r.nextBoolean() ? null : lorem.getFirstName()), 
					(r.nextBoolean() ? null : lorem.getParagraphs(1, 5)),
					(r.nextBoolean() ? null : lorem.getEmail()), 
					r.nextLong());
			
			write.write(p);
			
			Book read = new Book(p, sm);
			
			assertNotSame(write, read);
			assertEquals(write.author, read.author);
			assertEquals(write.isbn, read.isbn);
			assertEquals(write.description, read.description);
			assertEquals(write.url, read.url);
		}
	}

}
