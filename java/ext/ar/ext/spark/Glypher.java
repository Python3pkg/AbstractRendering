package ar.ext.spark;

import java.util.function.Function;

import ar.Glyph;
import ar.glyphsets.SimpleGlyph;
import ar.glyphsets.implicitgeometry.Indexed;
import ar.glyphsets.implicitgeometry.Shaper;

/**Wrap a shaper and a valuer up into a single object that produces glyphs.**/
public class Glypher<G,I> extends org.apache.spark.api.java.function.Function<Indexed, Glyph<G,I>> {
	private static final long serialVersionUID = -2010087917940244951L;
	
	final Shaper<Indexed, G> shaper;
	final Function<Indexed, I> valuer;
	public Glypher(Shaper<Indexed, G> shaper, Function<Indexed, I> valuer) {
		this.shaper=shaper;
		this.valuer=valuer;
	}

	public Glyph<G,I> call(Indexed item) throws Exception {
		return new SimpleGlyph<G,I>(shaper.apply(item), valuer.apply(item));
	}

}
