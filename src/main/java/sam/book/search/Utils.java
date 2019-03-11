package sam.book.search;

import org.json.JSONObject;

import sam.book.BooksHelper;
import sam.book.SmallBook;
import sam.books.PathsImpl;

public interface Utils {
	public static JSONObject toJson(SmallBook b, BooksHelper helper) {
		if(b == null)
			return new JSONObject();
		
		PathsImpl p = helper.getPath(b.path_id);
				
		return new JSONObject()
				.put("id",b.id)
				.put("page_count",b.page_count)
				.put("year",b.year)
				.put("path_id",b.path_id)
				.put("name",b.name)
				.put("filename",b.filename)
				.put("subpath",p.getPath()+"/"+b.filename)
				.put("status",String.valueOf(b.getStatus()));
	}

}
