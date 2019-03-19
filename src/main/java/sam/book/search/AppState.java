package sam.book.search;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.BitSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import sam.myutils.Checker;
import sam.string.StringUtils;

class AppState {
	
	Grouping grouping;
	Sorter sorter;
	boolean sorter_revered;
	Status2 choice; 
	BitSet dir_filter;
	BitSet sql;
	Set<String> set;
	String string;

	public void read(Path p) throws IOException {
		try(InputStream is = Files.newInputStream(p, READ);
				GZIPInputStream gis = new GZIPInputStream(is);
				InputStreamReader isr = new InputStreamReader(gis);
				BufferedReader reader = new BufferedReader(isr)) {

			Decoder d = Base64.getDecoder();
			grouping = parse(reader.readLine(), Grouping::valueOf);
			sorter = parse(reader.readLine(), Sorter::valueOf);
			sorter_revered = parse(reader.readLine(), Boolean::valueOf);
			choice = parse(reader.readLine(), Status2::valueOf); 
			dir_filter = Optional.ofNullable(parse(reader.readLine(), this::biset)).orElse(null);
			sql = Optional.ofNullable(parse(reader.readLine(), this::biset)).orElse(null);
			set = Optional.ofNullable(reader.readLine())
					.filter(Checker::isNotEmptyTrimmed)
					.map(s -> new String(d.decode(s)))
					.map(s -> StringUtils.splitStream(s, '\t').collect(Collectors.toSet())).orElse(null);
			
			string = decode(reader.readLine(), d);
		}
	}
	private String decode(String s, Decoder d) {
		if(Checker.isEmptyTrimmed(s))
			return null;

		return new String(d.decode(s));
	}
	private BitSet biset(String s) {
		if(Checker.isEmptyTrimmed(s))
			return null;
		return BitSet.valueOf(Base64.getDecoder().decode(s));
	}

	private <E> E parse(String s, Function<String, E> parser) {
		if(Checker.isEmptyTrimmed(s))
			return null;

		return parser.apply(s);
	}

	public void write(Path p) throws IOException {
		try(OutputStream is = Files.newOutputStream(p, WRITE, CREATE, TRUNCATE_EXISTING);
				GZIPOutputStream gis = new GZIPOutputStream(is);
				OutputStreamWriter isr = new OutputStreamWriter(gis);
				BufferedWriter w = new BufferedWriter(isr)) {

			Encoder e = Base64.getEncoder();

			write(grouping, w);
			write(sorter, w);
			write(sorter_revered, w);
			write(choice, w);
			write(dir_filter, w, s -> e.encodeToString(s.toByteArray()));
			write(sql, w, s -> e.encodeToString(s.toByteArray()));
			write(set, w, s -> encode(String.join("\t", s), e));
			write(string, w, s -> encode(s, e));
		}
	}

	private void write(Object o, BufferedWriter w) throws IOException {
		write(o, w, e -> e.toString());
	}
	private String encode(String s, Encoder e) {
		return e.encodeToString(s.getBytes());
	}

	private <E> void write(E e, BufferedWriter w, Function<E, String> mapper) throws IOException  {
		if(e != null)
			w.write(mapper.apply(e));
		w.newLine();
	}
	@Override
	public String toString() {
		JSONObject o = new JSONObject();
		o.put("grouping", grouping);
		o.put("sorter", sorter);
		o.put("sorter_revered", sorter_revered);
		o.put("choice", choice);
		o.put("dir_filter", dir_filter);
		o.put("sql", sql);
		o.put("set", new JSONArray(set));
		o.put("string", string);
		
		return o.toString(4);
	}
}
