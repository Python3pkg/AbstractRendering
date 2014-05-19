package ar.glyphsets;

import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.concurrent.ForkJoinPool;

import ar.Glyph;
import ar.Glyphset;
import ar.glyphsets.implicitgeometry.Indexed;
import ar.glyphsets.implicitgeometry.Indexed.Converter;
import ar.glyphsets.implicitgeometry.Shaper;
import ar.glyphsets.implicitgeometry.Valuer;

/**Given a file with line-oriented, regular-expression delimited values,
 * provides a list-like (read-only) interface.
 */
public class DelimitedFileList<G,I> implements Glyphset<G,I> {
	public static int DEFAULT_SKIP =0;

	
	/**The SEGMENT_FACTOR is used to make segments larger,
	 * and thus divide work into larger blocks.
	 * 
	 * Segmenting is derived from the number of bytes,
	 * but since this is not a fixed record-size format this can't be
	 * exact. Additionally, there is a non-trivial setup cost for each segment, so
	 * fewer larger segments is often advantageous. 
	 * 
	 */
	private static final long SEGMENT_FACTOR = 100000000L;   
	
	/**Source file.**/
	private final File source;
	
	/**Segment information for subsets.**/
	private final long segStart;
	private final long segEnd;
	
	/**Pattern used to delimit fields of the rows.**/
	private final String delimiters;
	
	/**Types of the fields.**/
	private final Converter.TYPE[] types;
	
	/**Number of lines to skip at the start of the file.**/
	private final int skip;

	private final Shaper<Indexed,G> shaper;
	private final Valuer<Indexed,I> valuer;

	///Cached items.
	private long size;
	private Rectangle2D bounds;

		
	public DelimitedFileList(File source, String delimiters, Converter.TYPE[] types, Shaper<Indexed,G> shaper, Valuer<Indexed, I> valuer) {this(source, delimiters, types, DEFAULT_SKIP, shaper, valuer);}
	public DelimitedFileList(File source, String delimiters, Converter.TYPE[] types, int skip, Shaper<Indexed,G> shaper, Valuer<Indexed, I> valuer) {this(source, delimiters, types, skip, shaper, valuer, 0, -1);}
	public DelimitedFileList(File source, String delimiters, Converter.TYPE[] types, int skip, Shaper<Indexed,G> shaper, Valuer<Indexed, I> valuer, long segStart, long segEnd) {
		this.source = source;
		this.delimiters = delimiters;
		this.types = types;
		this.skip = skip;
		this.shaper = shaper;
		this.valuer = valuer;
		this.segStart = segStart;
		this.segEnd = segEnd;
	}

	
	@Override
	public Rectangle2D bounds() {
		System.out.println("Bounds start...");
		if (bounds == null) {
			ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
			bounds = pool.invoke(new BoundsTask<>(this, 100000));
		}
		System.out.println("Bounds end...");
		return bounds;
	}
	
	@Override
	public long segments() {
		long size = source.length();
		long segs = size/(types.length*SEGMENT_FACTOR);
		System.out.println(segs);
		return segs;
	}
	
	@Override
	public Glyphset<G, I> segment(long bottom, long top) throws IllegalArgumentException {
		return new DelimitedFileList<>(source, delimiters, types, skip, shaper, valuer, bottom, top);
	}
	
	@Override public boolean isEmpty() {return size ==0;}
	@Override public Iterator iterator() {return new Iterator();}

	@Override
	public long size() {
		if (size <0) {
			try (BufferedReader r = new BufferedReader(new FileReader(source))) {
				size=0;
				while(r.readLine() != null) {size++;}
			} catch (IOException e) {
				throw new RuntimeException("Error processing file: " + source.getName());
			}
		}
		size = size-skip;
		return size;
	}
	
	/**Utility for converting a given string (i.e., line of a file) into an 'Indexed' instance.**/
	public static Indexed asIndexed(String line, String delimiters, Converter conv) {
		StringTokenizer t = new StringTokenizer(line, delimiters);
		String[] parts = new String[conv.size()];
		for (int i=0; i<parts.length; i++) {parts[i] = t.nextToken();}
		Indexed base = conv.applyTo(new Indexed.ArrayWrapper(parts));
		return base;
	}
	
	private final class Iterator implements java.util.Iterator<Glyph<G,I>> {
		private final Converter conv = new Converter(types);
		private final BufferedReader reader;

		private final long stop = segEnd * types.length * SEGMENT_FACTOR;
		private long charsRead;
		private String cache;
		private boolean closed = false;
		
		
		public Iterator() {
			try {
				reader = new BufferedReader(new FileReader(source));
				
				//Get to the first record-start in the segment
				long start = segStart*types.length*SEGMENT_FACTOR;
				reader.skip(start);

				if (segStart == 0) {
					for (long i=skip; i>0; i--) {reader.readLine();}					
				} else {
					reader.readLine();
				}
			} catch (IOException e) {
				throw new RuntimeException("Error initializing iterator for " + source.getName(), e);
			}
		}
		
		@Override
		protected void finalize() {
			try {if (!closed) {reader.close();}}
			catch (IOException e) {e.printStackTrace();}
		}
		
		@Override
		public boolean hasNext() {
			if (!closed && cache == null) {
				try {
					if (stop > 0 && charsRead > stop) {
						reader.close();
						closed = true;
						return false;
					}
					cache = reader.readLine();
					if (cache == null) {return false;}
					
					charsRead += cache.length();
				} catch (IOException e) {throw new RuntimeException("Error processing file: " + source.getName());}
			}
			return cache != null;
		}

		
		@Override
		public Glyph<G,I> next() {
			if (cache == null && !hasNext()) {throw new NoSuchElementException();}
			//System.out.printf("Processed %d%n", byteOffset);

			String line = cache;
			cache = null;
			Indexed base = asIndexed(line, delimiters, conv);
			return new SimpleGlyph<>(shaper.shape(base), valuer.value(base));
		}

		@Override public void remove() {throw new UnsupportedOperationException();}
	}
}
