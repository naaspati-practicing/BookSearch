package sam.book.search;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.book.Book;
import sam.books.BooksDB;
import sam.collection.MappedIterator;
import sam.fx.alert.FxAlert;
import sam.io.BufferConsumer;
import sam.io.BufferSupplier;
import sam.io.IOUtils;
import sam.io.serilizers.StringIOUtils;
import sam.myutils.Checker;
import sam.myutils.MyUtilsPath;
import sam.myutils.System2;
import sam.nopkg.Resources;

public class ResourceHelper {
	private static final Logger logger = LoggerFactory.getLogger(ResourceHelper.class);
	
	public static class Resource {
		private final int id;
		private final Path subpath;
		
		public Resource(int id, String subpath) {
			this.id  = id;
			this.subpath = Paths.get(subpath);
		}
		public Resource(Path subpath) {
			this.subpath = subpath;
			String s = subpath.getFileName().toString();

			if(!(s.endsWith(".zip") || s.endsWith(".7z"))) {
				this.id = -1;
			} else {
				int n = id(s, '-');
				this.id = n == -1 ? id(s, '_') : n;
			}
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
			return "Resource [id=" + id + ", path=" + subpath + "]";
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
					try(FileChannel fc = FileChannel.open(p, StandardOpenOption.READ);
							Resources r = Resources.get()) {
						ByteBuffer buffer = r.buffer();
						if(fc.read(buffer) < 4) 
							resources = new ArrayList<>();
						else {
							buffer.flip();
							int size = buffer.getInt();
							if(size == 0)
								resources = new ArrayList<>();
							else {
								resources = new ArrayList<>(size);
								int[] ids = new int[size];
								for (int i = 0; i < size; i++) {
									IOUtils.readIf(buffer, fc, 4);
									ids[i] = buffer.getInt();
								}
								IOUtils.compactOrClear(buffer);
								StringIOUtils.collect(BufferSupplier.of(fc, buffer), '\n', s -> resources.add(new Resource(ids[resources.size()], s)), r.decoder(), r.chars(), r.sb());
							}
							logger.debug("cache loaded: {}", p);
						}
					}
				}
			} catch (Exception e) {
				FxAlert.showErrorDialog(RESOURCE_DIR, "failed to reloaded resouece list", e);
				getResourcesError = true;
				return Collections.EMPTY_LIST;
			} 
		}
		List<Path> list = resources.stream().filter(r -> r.id == book.book.id).map(r -> r.subpath).collect(Collectors.toList());
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
			
			try(FileChannel fc = FileChannel.open(cachepath, CREATE, TRUNCATE_EXISTING, WRITE);
					Resources r = Resources.get();) {
				
				ByteBuffer buffer = r.buffer();
				
				for (Resource c : resources) {
					IOUtils.writeIf(buffer, fc, 4);
					buffer.putInt(c.id);
				}
				
				IOUtils.write(buffer, fc, true);
				StringIOUtils.writeJoining(new MappedIterator<>(resources.iterator(), m -> m.subpath.toString()), "\n", BufferConsumer.of(fc, false), buffer, r.chars(), r.encoder());
				logger.debug("recached : {}", cachepath);
			}
		} catch (IOException e2) {
			FxAlert.showErrorDialog(RESOURCE_DIR, "failed to reloaded resouece list", e2);
		}
	}

}
