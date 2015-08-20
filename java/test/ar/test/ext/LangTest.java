package ar.test.ext;

import static org.junit.Assert.*;

import org.fusesource.jansi.Ansi.Color;
import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static ar.ext.lang.Parser.*;
import static ar.ext.lang.Parser.TreeNode.*;

public class LangTest {
	@Test
	public void tokenize() {
		assertThat(tokens("test"), contains("test"));
		assertThat(tokens("(test)"), contains("(", "test", ")"));
		assertThat(tokens("(test of some more)"), contains("(", "test", "of", "some", "more", ")"));
		assertThat(tokens("((nested))"), contains("(", "(", "nested", ")", ")"));
		assertThat(tokens("((nested (deeply)) shallowly)"), contains("(", "(", "nested", "(", "deeply", ")", ")", "shallowly", ")"));
	}
	
	@Test
	public void tree() {
		assertThat(parse("test"), is(equalTo(leaf("test"))));
		assertThat(parse("(test)"), is(equalTo(inner(leaf("test")))));
		assertThat(parse("(test of some more)"), is(equalTo(inner(leaf("test"), leaf("of"), leaf("some"), leaf("more")))));
		assertThat(parse("(nested)"), is(equalTo(inner(leaf("nested")))));
		assertThat(parse("((nested (deeply)) shallowly)"), is(equalTo(inner(inner(leaf("nested"), inner(leaf("deeply"))), leaf("shallowly")))));
	}
	
	@Test
	public void reifyAtom() {
		assertThat(reify(parse("1")), is(Integer.valueOf(1)));
		assertThat(reify(parse("1.3")), is(Double.valueOf(1.3)));
		assertThat(reify(parse("1.3b")), is("1.3b"));
		assertThat(reify(parse("hello")), is("hello"));
	}
	
	@Test
	public void reifyNested() {
		assertThat(reify(parse("(rgb 0 0 0)")), is(Color.BLACK));
	}
}
