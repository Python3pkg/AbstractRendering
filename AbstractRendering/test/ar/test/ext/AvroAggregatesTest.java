package ar.test.ext;

import static org.junit.Assert.*;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.apache.avro.Schema;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.BeforeClass;
import org.junit.Test;

import ar.Aggregates;
import ar.Glyphset;
import ar.Renderer;
import ar.glyphsets.DynamicQuadTree;
import ar.renderers.ParallelSpatial;
import ar.rules.Aggregators;
import ar.rules.Aggregators.RLE;

import ar.app.util.GlyphsetUtils;
import ar.ext.avro.AggregateSerializer;
import ar.ext.avro.AggregateSerializer.FORMAT;
import ar.ext.avro.Converters;
import ar.ext.avro.SchemaComposer;

public class AvroAggregatesTest {
	public static Aggregates<Integer> count;
	public static Aggregates<RLE> rles;
	
	@BeforeClass
	public static void load() throws Exception {
		Glyphset glyphs = GlyphsetUtils.autoLoad(new File("../data/circlepoints.csv"), .1, DynamicQuadTree.make(Color.class));
		Renderer r = new ParallelSpatial();
		AffineTransform ivt = new AffineTransform(241.4615556310524, 
				0.0, 
				0.0, 
				241.4615556310524,
				238.49100176586487, 
				236.13546883394775).createInverse();
		count = r.reduce(glyphs, new Aggregators.Count(), ivt, 500,500);
		rles = r.reduce(glyphs, new Aggregators.RLEColor(true), ivt, 500,500);
	}
	
	@Test
	public void countsRoundTrip() throws Exception {
		Aggregates<Integer> ref = count;
		String filename ="./testResults/counts.avro";
		Schema s = new SchemaComposer().addResource("ar/ext/avro/count.avsc").resolved();
		OutputStream out = new FileOutputStream(filename);
		AggregateSerializer.serialize(ref, out, s, new Converters.FromCount(s));
		Aggregates<Integer> res = AggregateSerializer.deserialize(filename, new Converters.ToCount());

		assertEquals(ref.lowX(), res.lowX());
		assertEquals(ref.lowY(), res.lowY());
		assertEquals(ref.highX(), res.highX());
		assertEquals(ref.highY(), res.highY());
		assertEquals(ref.defaultValue(), res.defaultValue());
		
		for (int x=ref.lowX(); x<ref.highX(); x++) {
			for (int y=ref.lowY(); y<ref.highY(); y++)
				assertEquals(String.format("Value at (%d, %d)",x,y), ref.at(x, y), res.at(x, y));
		}

	}
	
	@Test
	public void CountToJSON() throws Exception {
		Aggregates<Integer> ref = count;
		Schema s = new SchemaComposer().addResource("ar/ext/avro/count.avsc").resolved();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		AggregateSerializer.serialize(ref, baos, s, FORMAT.JSON, new Converters.FromCount(s));
		String output = new String(baos.toByteArray(), "UTF-8");
		JsonParser p = new JsonFactory().createJsonParser(output);		
		ObjectMapper mapper = new ObjectMapper();
		JsonNode n = mapper.readTree(p);
		
		
		assertEquals(ref.lowX(), n.get("lowX").getIntValue());
		assertEquals(ref.lowY(), n.get("lowY").getIntValue());
		assertEquals(ref.highX(), n.get("highX").getIntValue());
		assertEquals(ref.highY(), n.get("highY").getIntValue());
	}
	
	
	@Test
	public void RLERoundTrip() throws Exception {
		Aggregates<RLE> ref = rles;
		
		String filename = "./testResults/rle.avro";
		OutputStream out = new FileOutputStream(filename);
		Schema s = new SchemaComposer().addResource("ar/ext/avro/rle.avsc").resolved();
		AggregateSerializer.serialize(ref, out, s, new Converters.FromRLE(s));
		Aggregates<RLE> res = AggregateSerializer.deserialize(filename, new Converters.ToRLE());

		assertEquals(ref.lowX(), res.lowX());
		assertEquals(ref.lowY(), res.lowY());
		assertEquals(ref.highX(), res.highX());
		assertEquals(ref.highY(), res.highY());
		assertEquals(ref.defaultValue(), res.defaultValue());
		
		for (int x=ref.lowX(); x<ref.highX(); x++) {
			for (int y=ref.lowY(); y<ref.highY(); y++) {
				RLE rref = ref.at(x,y);
				RLE rres = res.at(x,y);
				assertEquals(String.format("Unequal key count at (%d, %d)", x,y), rres.size(), rref.size());
				assertEquals("Unequal counts.", rref.counts,  rres.counts);
				
				//HACK: Must test string-equality because generic serialization is based on string category key
				assertEquals(Arrays.deepToString(rres.keys.toArray()), Arrays.deepToString(rref.keys.toArray()));
			}
		}
	}
	
	@Test
	public void readOcculusTile() throws Exception {
		String filename = "../data/avroTiles/0/0/0.avro";
		Aggregates<Integer> res = AggregateSerializer.deserializeTile(filename, new Converters.ToCount(),0,0,255,255);
		
		assertEquals(0, res.lowX());
		assertEquals(0, res.lowY());
		assertEquals(255, res.highX());
		assertEquals(255, res.highY());
	}
}
