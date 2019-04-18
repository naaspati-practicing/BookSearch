package sam.book.search;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import sam.book.Book;
import sam.books.BooksDB;
import sam.fx.alert.FxAlert;
import sam.io.serilizers.DataReader;
import sam.io.serilizers.DataWriter;
import sam.myutils.Checker;
import sam.myutils.MyUtilsPath;
import sam.myutils.System2;
import sam.nopkg.EnsureSingleton;
import sam.nopkg.Resources;

public class ResourceHelper {
    private static final EnsureSingleton SINGLETON = new EnsureSingleton();
    {
        SINGLETON.init();
    }
    
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

        public Resource(DataReader w) throws IOException {
            this.id = w.readInt();
            this.subpath = Paths.get(w.readUTF());
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
        public void write(DataWriter w) throws IOException {
            w.writeInt(id);
            w.writeUTF(subpath.toString());
        }
    }

   static final Path RESOURCE_DIR = Optional.ofNullable(System2.lookup("RESOURCE_DIR")).map(Paths::get).orElse(BooksDB.ROOT.resolve("non-book materials"));
    private List<Resource> resources;
    private boolean getResourcesError = false; 
    
    private Path resourceList_dat() {
        return Paths.get("resourcelist.dat");
    }

    public List<Path> getResources(Book book){
        if(getResourcesError)
            return emptyList();
        
        List<Path> list = resources().isEmpty() ? emptyList() : resources.stream().filter(r -> r.id == book.book.id).map(r -> r.subpath).collect(Collectors.toList());
        return Checker.isEmpty(list) ? emptyList() : unmodifiableList(list);
    }

    private List<Resource> resources() {
        if(resources != null)
            return resources;
        
        try {
            Path p = resourceList_dat();
            if(Files.notExists(p)) {
                _reloadResoueces(p);
            } else {
                try(FileChannel fc = FileChannel.open(p, READ);
                        Resources r = Resources.get();
                        DataReader w = new DataReader(fc, r.buffer());
                        ) {
                    w.setChars(r.chars());
                    w.setStringBuilder(r.sb());
                    
                    int size = w.readInt();
                    resources = new ArrayList<>(size);
                    
                    for (int i = 0; i < size; i++) 
                        resources.add(new Resource(w));
                    
                    System.out.println("read: "+p);
                }
            }
        } catch (Exception e) {
            FxAlert.showErrorDialog(RESOURCE_DIR, "failed to reloaded resouece list", e);
            getResourcesError = true;
            return emptyList();
        }
        
        return resources;
    }

    public void reloadResoueces() {
        _reloadResoueces(resourceList_dat());
    }
    void _reloadResoueces(Path cachepath) {
        try {
            resources = Files.walk(RESOURCE_DIR)
                    .skip(1)
                    .map(MyUtilsPath.subpather(RESOURCE_DIR))
                    .map(Resource::new)
                    .filter(f -> f.id >= 0)
                    .collect(Collectors.toList());
            
            try(FileChannel fc = FileChannel.open(cachepath, CREATE, TRUNCATE_EXISTING, WRITE);
                    Resources r = Resources.get();
                    DataWriter w = new DataWriter(fc, r.buffer());
                    ) {
                w.writeInt(resources.size());
                
                for (Resource d : resources)
                    d.write(w);

                System.out.println("recached : "+ cachepath);
            }
        } catch (IOException e2) {
            FxAlert.showErrorDialog(RESOURCE_DIR, "failed to reloaded resouece list", e2);
        }
    }

}
