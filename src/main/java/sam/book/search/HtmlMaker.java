package sam.book.search;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import sam.book.BooksHelper;
import sam.book.SmallBook;
import sam.books.BooksDB;
import sam.books.PathsImpl;

public class HtmlMaker {

	public static StringBuilder toHtml(Consumer<StringBuilder> bodyGenerate) {
		String title = "book list";
		StringBuilder sb = new StringBuilder("<!DOCTYPE html>\r\n<html>\r\n<head>\r\n ")
				.append("<meta charset=\"utf-8\" />\r\n    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\r\n    <title>")
				.append(title)
				.append("</title>\r\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n</head>\r\n<style>    td, th {         text-align:right;         padding:2px 10px 2px 10px;    }     td:nth-child(2),     th:nth-child(2) { text-align:left; }  </style>\r\n\r\n<body>\r\n    <section>\r\n <p id=\"stats\"></p>\r\n");
		
		bodyGenerate.accept(sb);
		
		sb.append("\r\n </section> <textarea id=\"clipboard\"></textarea>\r\n    \r\n")
		.append("<script> const basedir = '").append(BooksDB.ROOT.toString().replace('\\', '/')).append("/';\n")
		.append("const clipboard = document.getElementById('clipboard');\n")
		.append("const stats = document.getElementById('stats');\n")
		.append("clipboard.style.display = 'none';\n")
		.append("function copyAction(str) {\n")
		.append("  str = basedir.concat(str);\n")
		.append("  clipboard.style.display = 'block';\n")
		.append("  clipboard.value = str;\n")
		.append("  clipboard.select();\n")
		.append("  document.execCommand('copy');\n")
		.append("  clipboard.style.display = 'none';\n")
		.append("  stats.innerText = 'copied: '.concat(str);\n")
		.append("};\n")
		.append("</script></body>\r\n</html>");
		return sb;
	}

	public static StringBuilder toHtml(List<SmallBook> list, BooksHelper booksHelper) {
		return toHtml(sb -> appendList(sb, list, booksHelper));
	}

	private static void appendList(StringBuilder sb, List<SmallBook> books, BooksHelper helper) {
		sb.append("<table><tr>");
		
		for (String s : new String[]{
				"id",
				"name",
				"year",
				"page_count",
				"path_id",
				"status"
				}) {
			sb.append("<th>").append(s).append("</th>");
		}
		
		sb.append("\n</tr>\n");
		
		for (SmallBook b : books){
			PathsImpl p = helper.getPath(b.path_id);
			
			sb.append("<tr><td>").append(b.id).append(".</td>")
			.append("<td>").append(b.name).append("</td>")
			.append("<td>").append(b.year).append("</td>")
			.append("<td>").append(b.page_count).append("</td>")
			.append("<td>").append(b.path_id).append("</td>")
			.append("<td>").append(b.getStatus()).append("</td>")
			.append("<td><button onclick=\"copyAction('").append((p.getPath()+"/"+b.filename).replace('\\', '/')).append("')\">Copy</button></td>")
			.append("\n</tr>\n")
			;
		} 
		sb.append("\n</table>\n");
	}

	public static StringBuilder toHtml(Map<?, List<SmallBook>> map, BooksHelper helper) {
		return toHtml(sb -> {
			map.forEach((s,t) -> {
				sb.append("<h1>").append(s).append("</h1>\n");
				appendList(sb, t, helper);
			});
		});
	}
}
