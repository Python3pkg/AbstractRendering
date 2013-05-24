package ar.rules;

import java.util.HashSet;

import ar.AggregateReducer;
import ar.rules.Aggregators.RLE;


/**Example aggregate reducers**/
public class AggregateReducers {

	/**Combine counts by summing**/
	public static class Count implements AggregateReducer<Integer,Integer,Integer> {
		public Integer combine(Integer left, Integer right) {return left+right;}
		public String toString() {return "Count (int x int -> int)";}
	}

	/**Merge per-category counts.
	 * 
	 * Because function cannot know the original source interleaving,
	 * it cannot strictly merge run-length encodings.  Instead, it merges
	 * items by category and produces a new summation by category.
	 * This by-category summation is still stored in a run-length encoding
	 * for interaction with other methods that ignore RLE order.
	 * 
	 * TODO: Make a CoC class and an interface that captures what is shared between CoC and RLE.
	 * 
	 * **/
	public static class MergeCOC implements AggregateReducer<RLE,RLE,RLE> {
		public RLE combine(RLE left, RLE right) {
			if (left == null || left.size()==0) {return right;}
			if (right == null || left.size()==0) {return left;}
			
			HashSet<Object> categories = new HashSet<Object>();
			categories.addAll(left.keys);
			categories.addAll(right.keys);
			
			RLE total = new RLE();
			
			for (Object category: categories) {
				int v1 = left.val(category);
				int v2 = right.val(category);
				total.add(category, v1+v2);
			}
			return total;
		}
		
		public String toString() {return "CoC (RLE x RLE -> RLE)";}
	}

	
}