package sam.book;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import sam.io.serilizers.DataReader;
import sam.io.serilizers.DataWriter;
import sam.myutils.Checker;
import sam.nopkg.Resources;

interface ResourceSerializer {

    static Map<Integer, List<String>> read(Path p) throws IOException {
        if(Files.notExists(p))
            return new HashMap<>();
        
        try(FileChannel fc = FileChannel.open(p, READ);
                Resources r = Resources.get();
                DataReader w = new DataReader(fc, r.buffer())) {
            
            w.setChars(r.chars());
            w.setStringBuilder(r.sb());
            
            Map<Integer, List<String>> map = new HashMap<>();
            int size = w.readInt();
         
            for (int i = 0; i < size; i++) {
                int id = w.readInt();
                int s = w.readInt();
                List<String> list = new ArrayList<>(s);
                
                map.put(id, list);
                
                for (int j = 0; j < s; j++)
                    list.add(w.readUTF());
            }
            
            System.out.println("read: "+p);
            return map;
        }
    }

    static void write(Path p, Map<Integer, List<String>> map) throws IOException {
        if(Checker.isEmpty(map)) {
            Files.deleteIfExists(p);
            return;
        }
        
        
        try(FileChannel fc = FileChannel.open(p, CREATE, TRUNCATE_EXISTING, WRITE);
                Resources r = Resources.get();
                DataWriter w = new DataWriter(fc, r.buffer())) {
            
            map.values().removeIf(Checker::isEmpty);
            
            w.writeInt(map.size());
            
            for (Entry<Integer, List<String>> e : map.entrySet()) {
                w.writeInt(e.getKey());
                w.writeInt(e.getValue().size());
                
                for (String s : e.getValue()) 
                    w.writeUTF(s);
            }
            
            System.out.println("write: "+p);
        }
        
            
    }

}
