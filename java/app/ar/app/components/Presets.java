package ar.app.components;

import java.awt.Color;
import java.awt.Shape;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComboBox;
import javax.swing.JPanel;

import ar.Aggregates;
import ar.Aggregator;
import ar.Glyphset;
import ar.Renderer;
import ar.Transfer;
import ar.app.display.ARComponent;
import ar.app.display.SubsetDisplay;
import ar.app.util.GeoJSONTools;
import ar.app.util.GlyphsetUtils;
import ar.app.util.ActionProvider;
import ar.app.util.WrappedAggregator;
import ar.app.util.WrappedTransfer;
import ar.util.HasViewTransform;
import static ar.glyphsets.implicitgeometry.Valuer.*;
import static ar.glyphsets.implicitgeometry.Indexed.*;
import ar.glyphsets.DynamicQuadTree;
import ar.glyphsets.implicitgeometry.Indexed;
import ar.glyphsets.implicitgeometry.Valuer;
import ar.renderers.ParallelGlyphs;
import ar.renderers.ParallelSpatial;
import ar.rules.Advise;
import ar.rules.CategoricalCounts;
import ar.rules.Categories;
import ar.rules.General;
import ar.rules.Numbers;
import ar.rules.Advise.DrawDark;
import ar.rules.CategoricalCounts.CoC;
import ar.rules.Shapes;
import ar.rules.TransferMath;
import ar.util.ChainedTransfer;
import ar.util.Util;

public class Presets extends JPanel implements HasViewTransform {
	private static final long serialVersionUID = -5290930773909190497L;
	private final ActionProvider actionProvider = new ActionProvider();
	private static final Renderer CHAIN_RENDERER = new ParallelSpatial();

	private final JComboBox<Preset> presets = new JComboBox<Preset>();
	private final HasViewTransform transformSource;
	
	public Presets(HasViewTransform transformSource) {
		this.add(new LabeledItem("Presets:", presets));
		this.transformSource = transformSource;
		presets.addActionListener(actionProvider.delegateListener());
		
		ar.app.util.Util.loadInstances(presets, Presets.class, Presets.Preset.class, "");

		for (int i=0; i<presets.getItemCount(); i++) {
			Preset item = presets.getItemAt(i);
			boolean success = item.init(this);
			if (!success) {
				presets.removeItem(item); 
				i--;
			}
		}
	}
	
	
	/**Should the display be re-zoomed?  
	 * Returns true when the new glyphset & aggregator is not the same as the old one.**/
	public boolean doZoomWith(ARComponent.Aggregating oldPanel) {
		Preset p = (Preset) presets.getSelectedItem();

		return oldPanel == null
				|| oldPanel.dataset() != p.glyphset()
				|| !oldPanel.aggregator().equals(p.aggregator());
	}
	
	public SubsetDisplay update(ARComponent.Aggregating oldPanel) {
		Preset p = (Preset) presets.getSelectedItem();
		SubsetDisplay newPanel = new SubsetDisplay(p.aggregator(), p.transfer(), p.glyphset(), p.renderer());
		if (oldPanel != null 
				&& newPanel.dataset() == oldPanel.dataset()
				&& newPanel.aggregator().equals(oldPanel.aggregator())) {
			try {
				newPanel.viewTransform(oldPanel.viewTransform());
				newPanel.aggregates(oldPanel.aggregates(), oldPanel.renderTransform());
			} catch (NoninvertibleTransformException e) {
				try {newPanel.viewTransform(new AffineTransform());}
				catch (NoninvertibleTransformException e1) {/**(Hopefully) Not possible, identity transform is invertible**/}
			}
		} else {
			newPanel.zoomFit();
		}
		return newPanel;
	}
	
	public void addActionListener(ActionListener l) {actionProvider.addActionListener(l);}
	
	public static interface Preset {
		public Aggregator<?,?> aggregator();
		public Transfer<?,?> transfer();
		public Renderer renderer();
		public Glyphset<?> glyphset();
		public String name();
		public boolean init(Presets panel);
	}
	
	/**Generate a descriptive name from the parts of the preset instance.**/
	public static String fullName(Preset preset) {
		Glyphset<?> glyphset = preset.glyphset();
		if (glyphset ==null) {
			return preset.name() + "**LOAD FAILED**";
		} else {
			String subset = glyphset.size() < 1000 ? "**Subset**" : "";
			return String.format("%s %s", preset.name(),  subset);
		}	
	}		

	public static class ScatterplotAlpha implements Preset {
		public Aggregator<?,?> aggregator() {return new Numbers.Count<Object>();}
		public Renderer renderer() {return new ParallelSpatial(100);}
		public Glyphset<?> glyphset() {return CIRCLE_SCATTER;}
		public Transfer<?,?> transfer() {return new WrappedTransfer.FixedAlpha().op();}
		public String name() {return "Scatterplot: 10% Alpha";}
		public String toString() {return fullName(this);}
		public boolean init(Presets panel) {return glyphset() != null;}
	}

	public static class ScatterplotHDALphaLin implements Preset {
		public Aggregator<?,?> aggregator() {return new Numbers.Count<Object>();}
		public Renderer renderer() {return new ParallelSpatial(100);}
		public Glyphset<?> glyphset() {return CIRCLE_SCATTER;}
		public Transfer<?,?> transfer() {return new WrappedTransfer.RedWhiteLinear().op();}
		public String name() {return "Scatterplot: HDAlpha (Linear)";}
		public String toString() {return fullName(this);}
		public boolean init(Presets panel) {return glyphset() != null;}
	}
	
	public static class ScatterplotHDALpha implements Preset {
		public Aggregator<?,?> aggregator() {return new Numbers.Count<Object>();}
		public Renderer renderer() {return new ParallelSpatial(100);}
		public Glyphset<?> glyphset() {return CIRCLE_SCATTER;}
		public Transfer<?,?> transfer() {return new WrappedTransfer.RedWhiteLog().op();}
		public String name() {return "Scatterplot: HDAlpha (log)";}
		public String toString() {return fullName(this);}
		public boolean init(Presets panel) {return glyphset() != null;}
	}
	
	public static class BoostMMAlphaHDAlpha implements Preset {
		public Aggregator<?,?> aggregator() {return new WrappedAggregator.RLEColors().op();}
		public Renderer renderer() {return new ParallelGlyphs(1000);}
		public Glyphset<?> glyphset() {return BOOST_MEMORY_MM;}
		public Transfer<?,?> transfer() {return new WrappedTransfer.HighAlphaLog().op();}
		public String name() {return "BGL Memory: HDAlpha Cache hits (log)";}		
		public String toString() {return fullName(this);}
		public boolean init(Presets panel) {return glyphset() != null;}
	}
	
	public static class BoostMMAlphaActivity implements Preset {
		public Aggregator<?,?> aggregator() {return new WrappedAggregator.RLEColors().op();}
		public Renderer renderer() {return new ParallelGlyphs(1000);}
		public Glyphset<?> glyphset() {return BOOST_MEMORY_MM;}
		public Transfer<?,?> transfer() {
			return new ChainedTransfer<Object, Object>(
					CHAIN_RENDERER,
					new Categories.ToCount<>(), 
					new WrappedTransfer.RedWhiteLog().op());
		}
		public String name() {return "BGL Memory: Activity (log)";}		
		public String toString() {return fullName(this);}
		public boolean init(Presets panel) {return glyphset() != null;}
	}
	
	public static class Kiva implements Preset {
		public Aggregator<?,?> aggregator() {return new Numbers.Count<Object>();}
		public Renderer renderer() {return new ParallelGlyphs(10000);}
		public Glyphset<?> glyphset() {return KIVA_ADJ;}
		public Transfer<?,?> transfer() {return new WrappedTransfer.RedWhiteLog().op();}
		public String name() {return "Kiva: HDAlpha";}
		public String toString() {return fullName(this);}
		public boolean init(Presets panel) {return glyphset() != null;}
	}
	
	public static class KivaDrawDark implements Preset {
		public Aggregator<?,?> aggregator() {return new Numbers.Count<Object>();}
		public Renderer renderer() {return new ParallelGlyphs(10000);}
		public Glyphset<?> glyphset() {return KIVA_ADJ;}
		public Transfer<?,?> transfer() {
			return new DrawDark(Color.black, Color.white, 6);
		}
		public String name() {return "Kiva: DrawDark";}
		public String toString() {return fullName(this);}
		public boolean init(Presets panel) {return glyphset() != null;}
	}
	
	public static class USPopMinAlpha implements Preset {
		public Aggregator<?,?> aggregator() {return new Categories.MergeCategories<>();}
		public Renderer renderer() {return new ParallelGlyphs(1000);}
		public Glyphset<?> glyphset() {return CENSUS_MM;}
		public Transfer<?,?> transfer() {
			return new ChainedTransfer<>(
				CHAIN_RENDERER,
				new Categories.ToCount<>(),
				new TransferMath.DivideInt(4000),
				new Numbers.FixedInterpolate(Color.white, Color.red, 0, 255));
		}
		public String name() {return "US Population (Min Alpha)";}
		public String toString() {return fullName(this);}
		public boolean init(Presets panel) {return glyphset() != null;}
	}
	

	public static class USPop10Pct implements Preset {
		public Aggregator<?,?> aggregator() {return new Categories.MergeCategories<>();}
		public Renderer renderer() {return new ParallelGlyphs(1000);}
		public Glyphset<?> glyphset() {return CENSUS_MM;}
		public Transfer<?,?> transfer() {
			return new ChainedTransfer<>(
				CHAIN_RENDERER,
				new Categories.ToCount<>(),
				new TransferMath.DivideInt(4000),
				new Numbers.FixedInterpolate(Color.white, Color.red, 0, 25));
		}
		public String name() {return "US Population 10% alpha";}
		public String toString() {return fullName(this);}
		public boolean init(Presets panel) {return glyphset() != null;}
	}
	
	public static class USPopulation implements Preset {
		public Aggregator<?,?> aggregator() {return new Categories.MergeCategories<>();}
		public Renderer renderer() {return new ParallelGlyphs(1000);}
		public Glyphset<?> glyphset() {return CENSUS_MM;}
		public Transfer<?,?> transfer() {
			return new ChainedTransfer<>(
					CHAIN_RENDERER, 
					new Categories.ToCount<>(), 
//					new  Numbers.Interpolate(new Color(255,0,0,30), new Color(255,0,0,255), Util.CLEAR, 2));
					new  Numbers.Interpolate(new Color(255,0,0,30), new Color(255,0,0,255)));
		}
		public String name() {return "US Population";}
		public String toString() {return fullName(this);}
		public boolean init(Presets panel) {return glyphset() != null;}
	}
	public static class USPopulationClipWarn implements Preset {
		public Aggregator<?,?> aggregator() {return new Categories.MergeCategories<>();}
		public Renderer renderer() {return new ParallelGlyphs(1000);}
		public Glyphset<CoC<String>> glyphset() {return CENSUS_MM;}
		public Transfer<?,?> transfer() {

			Transfer<CoC<Number>, Color> inner = 
					new ChainedTransfer<>(
							CHAIN_RENDERER,
							new Categories.ToCount<>(),
							new TransferMath.DivideInt(4000),
							new Numbers.FixedInterpolate(Color.white, Color.red, 0, 255));
			return new Advise.OverUnder(Color.BLACK, Color.gray, inner, new CategoricalCounts.CompareMagnitude(), 10);
		}
		public String name() {return "US Population (Clip Warn)";}
		public String toString() {return fullName(this);}
		public boolean init(Presets panel) {return glyphset() != null;}
	}
	
	public static class USPopulationWeave implements Preset {
		HasViewTransform transformProvider = null;
		private final List<Shape> shapes;
		
		public USPopulationWeave() {
			try {
				shapes = GeoJSONTools.flipY(GeoJSONTools.loadShapesJSON(new File("../data/maps/USStates"), false));
				//shapes = GeoJSONTools.flipY(GeoJSONTools.loadShapesJSON(new File("../data/maps/USCounties"), true));
			} catch (Exception e) {throw new RuntimeException(e);}
		}

		/**Provide the viewTransform-access pathway.**/
		public boolean init(Presets provider) {
			this.transformProvider = provider;
			return glyphset() != null;
		}
		public Aggregator<?,?> aggregator() {return new Categories.MergeCategories<>();}
		public Renderer renderer() {return new ParallelGlyphs(1000);}
		public Glyphset<?> glyphset() {return CENSUS_MM;}
		public Transfer<?,?> transfer() {
			try {
				Map<Object, Color> colors = new HashMap<>();
				colors.put(2, new Color(0,0,200));	//White
				colors.put(3, new Color(0,200,0));	//African American
				colors.put(4, new Color(220,0,0));	//Native American
				colors.put(5, Color.GRAY);	//Asian
				colors.put(6, Color.GRAY);	//Hawaiian
				colors.put(7, Color.GRAY);	//Other
				colors.put(8, Color.GRAY);	//Mixed
				Transfer<CategoricalCounts<Object>, CategoricalCounts<Color>> rekey = new Categories.ReKey<Object, Color>(new CoC<Color>(Util.COLOR_SORTER), colors, Color.BLACK);

				Transfer<CategoricalCounts<Color>, CoC<Color>> gather = new Shapes.ShapeGather(shapes, transformProvider);
				Transfer<CoC<Color>, Color> weave = new Categories.RandomWeave();
				Transfer<?, ?> chain = new ChainedTransfer<>(CHAIN_RENDERER, rekey, gather, weave);
				return chain;
			} catch (Exception e) {throw new RuntimeException("Error creating transfer.",e);}
		}
		public String name() {return "US Population (Weave)";}
		public String toString() {return fullName(this);}
	}
	

	
	public static class USRaces implements Preset {
		public Aggregator<?,?> aggregator() {return new Categories.MergeCategories<>();}
		public Renderer renderer() {return new ParallelGlyphs(1000);}
		public Glyphset<?> glyphset() {return CENSUS_MM;}
		public Transfer<?,?> transfer() {
			Map<Object, Color> colors = new HashMap<>();
			colors.put(2, new Color(0,0,200));	//White
			colors.put(3, new Color(0,200,0));	//African American
			colors.put(4, new Color(220,0,0));	//Native American
			colors.put(5, Color.GRAY);	//Asian
			colors.put(6, Color.GRAY);	//Hawaiian
			colors.put(7, Color.GRAY);	//Other
			colors.put(8, Color.GRAY);	//Mixed

			Transfer<CategoricalCounts<Object>, CategoricalCounts<Color>> rekey = new Categories.ReKey<Object, Color>(new CoC<Color>(Util.COLOR_SORTER), colors, Color.BLACK);
			Transfer<CategoricalCounts<Color>, Color> stratAlpha = new Categories.HighAlpha(Color.white, .1, true);
			return new ChainedTransfer<Object, Object>(
					CHAIN_RENDERER,
					rekey,
					stratAlpha);
		}
		public String name() {return "US Ratial Distribution";}
		public String toString() {return fullName(this);}
		public boolean init(Presets panel) {return glyphset() != null;}
	}
	

	public static class USRacesLift implements Preset {
		public Aggregator<?,?> aggregator() {return new Categories.MergeCategories<>();}
		public Renderer renderer() {return new ParallelGlyphs(1000);}
		public Glyphset<?> glyphset() {return CENSUS_MM;}
		public Transfer<?,?> transfer() {
			Map<Object, Color> colors = new HashMap<>();
			colors.put(2, new Color(0,0,200));	//White
			colors.put(3, new Color(0,200,0));	//African American
			colors.put(4, new Color(220,0,0));	//Native American
			colors.put(5, Color.GRAY);	//Asian
			colors.put(6, Color.GRAY);	//Hawaiian
			colors.put(7, Color.GRAY);	//Other
			colors.put(8, Color.GRAY);	//Mixed

			Transfer<CategoricalCounts<Object>, CategoricalCounts<Color>> rekey = new Categories.ReKey<Object, Color>(new CoC<Color>(Util.COLOR_SORTER), colors, Color.BLACK);
			Transfer<CategoricalCounts<Color>, Color> stratAlpha = new Categories.HighAlpha(Color.white, .1, true);
			Transfer<CategoricalCounts<Color>, Color> lift = new LiftIf(.1, stratAlpha);

			return new ChainedTransfer<Object, Object>(
					CHAIN_RENDERER,
					rekey,
					lift);
		}
		public String name() {return "US Ratial Distribution (highlight 'other')";}
		public String toString() {return fullName(this);}
		public boolean init(Presets panel) {return glyphset() != null;}
	}

	
	public static class Overplot implements Preset {
		public Aggregator<?,?> aggregator() {return new Numbers.Count<Object>();}
		public Renderer renderer() {return new ParallelSpatial(100);}
		public Glyphset<?> glyphset() {return CIRCLE_SCATTER;}
		public Transfer<?,?> transfer() {return new WrappedTransfer.OverUnder().op();}
		public String name() {return "Scatterplot: clipping warning";}
		public String toString() {return fullName(this);}
		public boolean init(Presets panel) {return glyphset() != null;}
	}
	
	private static final Glyphset<Color> CIRCLE_SCATTER; 
	private static final Glyphset<Color> KIVA_ADJ; 
	private static final Glyphset<Color> BOOST_MEMORY_MM; 
	private static final Glyphset<CoC<String>> CENSUS_MM;
	
	private static String MEM_VIS_BIN = "../data/MemVisScaled.hbin";
	private static String CIRCLE_CSV = "../data/circlepoints.csv";
	private static String KIVA_BIN = "../data/kiva-adj.hbin";
	private static String CENSUS = "../data/census/Race_TractLatLonDenorm.hbin";
	
	static {
		Glyphset<Color> boost_temp = null;
		try {boost_temp = GlyphsetUtils.memMap(
				"BGL Memory", MEM_VIS_BIN, 
				new Indexed.ToRect(.001, .001, true, 0, 1),
				new ToValue<>(2, new Binary<Integer,Color>(0, Color.BLUE, Color.RED)), 
				1, "ddi");
		} catch (Exception e) {
			System.err.printf("Error loading data from %s.  Related presets are unavailable.\n", MEM_VIS_BIN);
		}
		BOOST_MEMORY_MM = boost_temp;
		
		Glyphset<CoC<String>> census_temp = null;
		try {census_temp = GlyphsetUtils.memMap(
				"US Census", CENSUS, 
				new Indexed.ToRect(.2, .2, true, 0, 1),
				new Valuer.CategoryCount<>(new Util.ComparableComparator<String>(), 3,2),
				1, null);
		} catch (Exception e) {
			System.err.printf("Error loading data from %s.  Related presets are unavailable.\n", CENSUS);
		}
		CENSUS_MM = census_temp;

		
		Glyphset<Color> circle_temp = null;
		try {
			circle_temp = GlyphsetUtils.autoLoad(new File(CIRCLE_CSV), .1, DynamicQuadTree.<Color>make());
		} catch (Exception e) {
			System.err.printf("Error loading data from %s.  Related presets are unavailable.\n", CIRCLE_CSV);
		}
		CIRCLE_SCATTER = circle_temp;
		
		Glyphset<Color> kiva_temp = null;
		try {kiva_temp = GlyphsetUtils.memMap(
						"Kiva", KIVA_BIN, 
						new Indexed.ToRect(.1, 0, 1),
						new Valuer.Constant<Indexed, Color>(Color.RED), 
						1, null);
		} catch (Exception e) {
			System.err.printf("Error loading data from %s.  Related presets are unavailable.\n", KIVA_BIN);
		}
		KIVA_ADJ = kiva_temp;
	}
	
	

	///Predicate to lift the 'other' category to the front...
	private static class LiftIf extends General.Switch<CategoricalCounts<Color>, Color> {
		@SuppressWarnings("rawtypes")
		public LiftIf(double cutoff, Transfer<CategoricalCounts<Color>, Color> baseline) {
			super(
				new Pred(cutoff), 
				new General.Const(Color.black), 
				baseline, 
				baseline.emptyValue());
		}
		
		private static final class Pred implements General.Switch.Predicate.Specialized<CategoricalCounts<Color>> {
			private final double cutoff;
			public Pred(double cutoff) {this.cutoff = cutoff;}
         
			public boolean test(int x, int y,
					Aggregates<? extends CategoricalCounts<Color>> aggs) {
				
				CategoricalCounts<Color> val = aggs.get(x, y);				
				int keyIdx=-1;
				for (int i=0; i< val.size(); i++) {
					if (val.key(i) == Color.GRAY) {keyIdx = i; break;}
				}
				return (keyIdx >=0 && val.count(keyIdx)/((double) val.fullSize()) > cutoff);
			}

			public ar.rules.General.Switch.Predicate.Specialized<CategoricalCounts<Color>> 
				specialize(Aggregates<? extends CategoricalCounts<Color>> aggs) {
				return this;
			}
		}
		
	}

	@Override
	public AffineTransform viewTransform() {return transformSource.viewTransform();}

	@Override
	public void viewTransform(AffineTransform vt)
			throws NoninvertibleTransformException {transformSource.viewTransform(vt);}
	
}