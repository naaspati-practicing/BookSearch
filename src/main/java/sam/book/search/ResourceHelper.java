package sam.book.search;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
	
	public static class Resource {
		private final int id;
		private final Path path;

		public Resource(DataInputStream dis) throws IOException {
			this.id = dis.readInt();
			this.path = Paths.get(dis.readUTF());
		}
		public Resource(Path subpath) {
			this.path = subpath;
			String s = subpath.getFileName().toString();

			if(!(s.endsWith(".zip") || s.endsWith(".7z"))) {
				this.id = -1;
			} else {
				int n = id(s, '-');
				this.id = n == -1 ? id(s, '_') : n;
			}
		}
		
		public void write(DataOutputStream dos) throws IOException {
			dos.writeInt(id);
			dos.writeUTF(path.toString());
		}

		private int id(String s, char c) {
			int n = s.indexOf(c);
			if(n < 0)
				return -1;
			try {
				return Integer.parseInt(s.substring(0, n));
			} catch (NumberFormatException e) {
				return -1;
			}
		}
		@Override
		public String toString() {
			return "Resource [id=" + id + ", path=" + path + "]";
		}
	}

	static final Path RESOURCE_DIR = Optional.ofNullable(System2.lookup("RESOURCE_DIR")).map(Paths::get).orElse(BooksDB.ROOT.resolve("non-book materials"));
	private static List<Resource> resources;
	private static boolean getResourcesError = false; 
	
	private static Path resourceList_dat() {
		return Paths.get("resourcelist.dat");
	}

	@SuppressWarnings("unchecked")
	public static List<Path> getResources(Book book){
		if(getResourcesError)
			return Collections.EMPTY_LIST;
		
		if(resources == null) {
			try {
				Path p = resourceList_dat();
				if(Files.notExists(p)) {
					_reloadResoueces(p);
				} else {
					resources = ObjectReader.readList(p, Resource::new);
					System.out.println("cache loaded: "+p);
				}
			} catch (Exception e) {
				FxAlert.showErrorDialog(RESOURCE_DIR, "failed to reloaded resouece list", e);
				getResourcesError = true;
				return Collections.EMPTY_LIST;
			} 
		}
		List<Path> list = resources.stream().filter(r -> r.id == book.book.id).map(r -> r.path).collect(Collectors.toList());
		return Checker.isEmpty(list) ? Collections.EMPTY_LIST : Collections.unmodifiableList(list);
	}

	public static void reloadResoueces() {
		_reloadResoueces(resourceList_dat());
	}
	static void _reloadResoueces(Path cachepath) {
		try {
			resources = Files.walk(RESOURCE_DIR)
					.skip(1)
					.map(MyUtilsPath.subpather(RESOURCE_DIR))
					.map(Resource::new)
					.filter(f -> f.id >= 0)
					.collect(Collectors.toList());

			ObjectWriter.writeList(cachepath, resources, Resource::write);
			System.out.println("recached : "+cachepath);
		} catch (IOException e2) {
			FxAlert.showErrorDialog(RESOURCE_DIR, "failed to reloaded resouece list", e2);
		}
	}

}
