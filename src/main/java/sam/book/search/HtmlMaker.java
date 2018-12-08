package sam.book.search;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import sam.book.SmallBook;
import sam.books.BooksDB;

public class HtmlMaker {

	public void create(List<SmallBook> books, Path path) throws UnsupportedEncodingException, IOException {
		String title = "book list";
		StringBuilder sb = new StringBuilder("<!DOCTYPE html>\r\n<html>\r\n<head>\r\n    <base href=\"")
				.append(BooksDB.ROOT)
				.append("\"> \r\n    <meta charset=\"utf-8\" />\r\n    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\r\n    <title>")
				.append(title)
				.append("</title>\r\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n</head>\r\n<style>\r\n    li {\r\n        list-style-type: none;\r\n        padding-bottom: 1em;\r\n        border-width: 0 0 0.1px 0;\r\n        border-color: black;\r\n        border-style: solid;\r\n    }\r\n      p {\r\n          margin: 0.2em;\r\n          font-family: 'Courier New', Courier, monospace;\r\n      }\r\n      p.title {\r\n          font-size: 1.2em;\r\n      }\r\n      p.path {\r\n          font-size: 0.8em;\r\n          text-decoration: underline blue;\r\n          color: blue;\r\n      }\r\n      p.others {\r\n          font-size: 0.7em;\r\n      }\r\n</style>\r\n\r\n<body>\r\n    <section>\r\n        <ul>\r\n");
		
		for (SmallBook book : books) 
			append(sb, book);

		sb.append("\r\n</ul>\r\n    </section>\r\n    \r\n</body>\r\n</html>");
		
		Files.write(path, sb.toString().getBytes("utf-8"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}
	
	private void append(StringBuilder sb, SmallBook book) {
		sb.append("<li>\r\n<p class=\"title\">")
		.append(book.name)
		.append("</p>\n<p class=\"others\">id: ")
		.append(book.id)
		.append(" | pages: ")
		.append(book.page_count)
		.append(" | year: ")
		.append(book.year)
		.append("</p>\n</li>\n");
	}

}
