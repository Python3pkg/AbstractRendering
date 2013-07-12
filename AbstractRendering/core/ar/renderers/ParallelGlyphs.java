package ar.renderers;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

import ar.AggregateReducer;
import ar.Aggregates;
import ar.Aggregator;
import ar.Glyphset;
import ar.Glyphset.Glyph;
import ar.aggregates.ConstantAggregates;
import ar.aggregates.FlatAggregates;
import ar.glyphsets.GlyphSingleton;
import ar.util.Util;
import ar.Renderer;
import ar.Transfer;


/**Task-stealing renderer that works on a per-glyph basis, designed for use with a linear stored glyph-set.
 * Iterates the glyphs and produces many aggregate sets that are then combined
 * (i.e., glyph-driven iteration).
 * 
 * TODO: Extend beyond aggregate reducers with same LEFT/RIGHT/OUT
 */
public class ParallelGlyphs implements Renderer {
	public static int DEFAULT_TASK_SIZE = 100000;
	public static int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
	private final ForkJoinPool pool = new ForkJoinPool(THREAD_POOL_SIZE);

	private final int taskSize;
	private final AggregateReducer<?,?,?> reducer;
	private final RenderUtils.Progress recorder;

	public <A> ParallelGlyphs(AggregateReducer<A,A,A> red) {
		this(DEFAULT_TASK_SIZE, red);
	}
	
	public <A> ParallelGlyphs(int taskSize, AggregateReducer<A,A,A> red) {
		this.taskSize = taskSize;
		this.reducer = red;
		recorder = RenderUtils.recorder();
	}
	
	protected void finalize() {pool.shutdownNow();}

	@Override
	public <V,A> Aggregates<A> reduce(Glyphset<? extends V> glyphs, Aggregator<V,A> op, 
			AffineTransform inverseView, int width, int height) {
		
		AffineTransform view;
		try {view = inverseView.createInverse();}
		catch (Exception e) {throw new RuntimeException("Error inverting the inverse-view transform....");}
		recorder.reset(glyphs.size());
		
		if (!reducer.left().isAssignableFrom(op.output())) {
			throw new IllegalArgumentException("Reducer type does not match aggregator type.");
		}
		
		ReduceTask<V,A> t = new ReduceTask<V,A>(
				(Glyphset.Segementable<V>) glyphs, 
				view, inverseView, 
				op, 
				(AggregateReducer<A,A,A>) reducer, 
				width, height, taskSize,
				recorder,
				0, glyphs.size());
		
		Aggregates<A> a= pool.invoke(t);
		
		return a;
	}
	
	
	public <IN,OUT> Aggregates<OUT> transfer(Aggregates<? extends IN> aggregates, Transfer<IN,OUT> t) {
		return new SerialSpatial().transfer(aggregates, t);
	}
	
	public double progress() {return recorder.percent();}

	private static final class ReduceTask<G,A> extends RecursiveTask<Aggregates<A>> {
		private static final long serialVersionUID = 705015978061576950L;

		private final int taskSize;
		private final long low;
		private final long high;
		private final Glyphset.Segementable<G> glyphs;		//TODO: Can some hackery be done with iterators instead so generalized GlyphSet can be used?  At what cost??
		private final AffineTransform view, inverseView;
		private final int width;
		private final int height;
		private final AggregateReducer<A,A,A> reducer;
		private final Aggregator<G,A> op;
		private final RenderUtils.Progress recorder;

		
		public ReduceTask(Glyphset.Segementable<G> glyphs, 
				AffineTransform view, AffineTransform inverseView,
				Aggregator<G,A> op, AggregateReducer<A,A,A> reducer, 
				int width, int height, int taskSize,
				RenderUtils.Progress recorder,
				long low, long high) {
			this.glyphs = glyphs;
			this.view = view;
			this.inverseView = inverseView;
			this.op = op;
			this.reducer = reducer;
			this.width = width;
			this.height = height;
			this.taskSize = taskSize;
			this.recorder = recorder;
			this.low = low;
			this.high = high;
		}

		protected Aggregates<A> compute() {
			if ((high-low) > taskSize) {return split();}
			else {return local();}
		}
		
		private final Aggregates<A> split() {
			long mid = low+((high-low)/2);

			ReduceTask<G,A> top = new ReduceTask<G,A>(glyphs, view, inverseView, op, reducer, width,height, taskSize, recorder, low, mid);
			ReduceTask<G,A> bottom = new ReduceTask<G,A>(glyphs, view, inverseView, op, reducer, width,height, taskSize, recorder, mid, high);
			invokeAll(top, bottom);
			Aggregates<A> aggs = AggregateReducer.Strategies.foldLeft(top.getRawResult(), bottom.getRawResult(), reducer);
			return aggs;
		}
		
		//TODO: Respect the actual shape.  Currently assumes that the bounds box matches the actual item bounds..
		private final Aggregates<A> local() {
			Glyphset.Segementable<G> subset = glyphs.segement(low,  high);
			Rectangle bounds = view.createTransformedShape(Util.bounds(subset)).getBounds();
			bounds = bounds.intersection(new Rectangle(0,0,width,height));
			
			if (bounds.isEmpty()) {
				int x2 = bounds.x+bounds.width;
				int y2 = bounds.y+bounds.height;
				return new ConstantAggregates<A>(Math.min(x2, bounds.x), Math.min(y2, bounds.y),
												Math.max(x2, bounds.x), Math.min(y2, bounds.y),
												op.identity());
			}				
			Aggregates<A> aggregates = new FlatAggregates<A>(bounds.x, bounds.y,
														 bounds.x+bounds.width, bounds.y+bounds.height, 
														 op.identity());
			
			
			Point2D lowP = new Point2D.Double();
			Point2D highP = new Point2D.Double();
			
			for (Glyph<G> g: subset) {
				//Discretize the glyph into the aggregates array
				Rectangle2D b = g.shape().getBounds2D();
				lowP.setLocation(b.getMinX(), b.getMinY());
				highP.setLocation(b.getMaxX(), b.getMaxY());
				
				view.transform(lowP, lowP);
				view.transform(highP, highP);
				
				int lowx = (int) Math.floor(lowP.getX());
				int lowy = (int) Math.floor(lowP.getY());
				int highx = (int) Math.ceil(highP.getX());
				int highy = (int) Math.ceil(highP.getY());

				Rectangle pixel = new Rectangle(lowx, lowy, 1,1);
				A v = op.at(pixel, new GlyphSingleton<G>(g, subset.valueType()), inverseView);
				
				
				for (int x=Math.max(0,lowx); x<highx && x<width; x++){
					for (int y=Math.max(0, lowy); y<highy && y<height; y++) {
						aggregates.set(x, y, reducer.combine(aggregates.at(x,y), v));
					}
				}
			}
			
			recorder.update(high-low);
			return aggregates;
		}
	}
}
