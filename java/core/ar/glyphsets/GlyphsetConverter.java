package ar.glyphsets;

import java.awt.geom.Rectangle2D;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import ar.Glyph;
import ar.Glyphset;
import ar.glyphsets.implicitgeometry.Valuer;
import ar.util.axis.DescriptorPair;

/**Convert the value types from one value to another via the provided converter.
 * Creates a glyph object, so the original glyphset is not modified.
 * However, if the glyphset is transiently realizing items, the underlying resources will
 * also not be copied. 
 *
 * @param <I> Original input value type
 * @param <V> Post-conversion value type
 */
public class GlyphsetConverter<G,I,V> implements Glyphset.RandomAccess<G,V> {
	protected final Glyphset<G,I> base;
	protected final Valuer<I,V> converter;
	protected final Glyphset.RandomAccess<G,I> randomAccess;
	
	public GlyphsetConverter(Glyphset<G,I> base, Valuer<I,V> converter) {
		this.base = base;
		this.converter = converter;
		if (base instanceof Glyphset.RandomAccess) {
			this.randomAccess = (Glyphset.RandomAccess<G,I>) base;
		} else {
			randomAccess = null;
		}
	}
	
	protected Glyph<G,V> wrap(Glyph<G,I> g) {
		return new SimpleGlyph<>(g.shape(), converter.apply(g.info()));
	}
	
	@Override
	public Iterator<Glyph<G,V>> iterator() {
		return new Iterator<Glyph<G,V>>() {
			Iterator<Glyph<G,I>> base = GlyphsetConverter.this.base.iterator();

			public void remove() {base.remove();}
			public boolean hasNext() {return base.hasNext();}
			public Glyph<G,V> next() {
				Glyph<G,I> g = base.next();
				return wrap(g);
			}
		};
	}

	@Override public boolean isEmpty() {return base.isEmpty();}
	@Override public Rectangle2D bounds() {return base.bounds();}
	@Override public long size() {return base.size();}
	@Override public DescriptorPair<?,?> axisDescriptors() {return base.axisDescriptors();}
	@Override public void axisDescriptors(DescriptorPair<?,?> descriptor) {base.axisDescriptors(descriptor);}

	@Override
	public List<Glyphset<G, V>> segment(int count) throws IllegalArgumentException {
		return base.segment(count).stream().map(s -> new GlyphsetConverter<>(s, converter)).collect(Collectors.toList());
	}
	
	@Override
	public Glyph<G,V> get(long l) {
		if (randomAccess != null) {return wrap(randomAccess.get(l));}
		else {throw new UnsupportedOperationException("Cannot perform random access because backing collection does not support it.");}
	}

	
}
