package sam.book.search;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import sam.book.Book;
import sam.books.BooksDB;
import sam.fx.alert.FxAlert;
import sam.io.serilizers.ObjectReader;
import sam.io.serilizers.ObjectWriter;
import sam.myutils.Checker;
import sam.myutils.MyUtilsPath;
import sam.myutils.System2;

public class ResourceHelper {

	static final Path RESOURCE_DIR = Optional.ofNullable(System2.lookup("RESOURCE_DIR")).map(Paths::get).orElse(BooksDB.ROOT.resolve("non-book materials"));
	private static Map<Integer, List<String>> resourceMap;
	private static boolean getResourcesError = false; 

	@SuppressWarnings("unchecked")
	public static List<String> getResources(Book book){
		if(getResourcesError)
			return Collections.EMPTY_LIST;
		
		if(resourceMap == null) {
			try {
				Path p = Paths.get("resourcelist.dat");
				if(Files.notExists(p))
					reloadResoueces();
				else
					resourceMap = ObjectReader.read(p);
			} catch (Exception e) {
				FxAlert.showErrorDialog(RESOURCE_DIR, "failed to reloaded resouece list", e);
				getResourcesError = true;
				return Collections.EMPTY_LIST;
			} 
		}
		List<String> list = resourceMap.get(book.book.id);
		return Checker.isEmpty(list) ? Collections.EMPTY_LIST : Collections.unmodifiableList(list);
	}

	public static void reloadResoueces() {
		try {
			Predicate<String> pattern = Pattern.compile("^\\d+-.+").asPredicate();

			resourceMap = Files.walk(RESOURCE_DIR)
					.filter(f -> pattern.test(f.getFileName().toString()))
					.map(MyUtilsPath.subpather(RESOURCE_DIR))
					.collect(Collectors.groupingBy(p -> Optional.of(p.getFileName().toString()).map(s -> s.substring(0, s.indexOf('-'))).map(Integer::parseInt).get(), Collectors.mapping(Path::toString, Collectors.toList())));

			ObjectWriter.write(Paths.get("resourcelist.dat"), resourceMap);
		} catch (IOException e2) {
			FxAlert.showErrorDialog(RESOURCE_DIR, "failed to reloaded resouece list", e2);
		}
	}

}
