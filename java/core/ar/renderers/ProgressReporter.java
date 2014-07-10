package ar.renderers;

import java.util.concurrent.atomic.AtomicLong;

/**Utility class for recording percent progress through a task.
 * 
 * Producer Side:
 * If the task size is known, then reset should be called with
 * the expected number of steps and update should be called
 * with a total number equaling the expected.
 * 
 * If the task size is not known, then reset should be called with zero as the expected size
 * at the start.  When done, reset should be called again with a negative value.
 * 
 * Consumer side:
 * Periodically call percent to find out the percentage of work complete.
 * If the task size was known, these will return appropriate values.
 * If the task size is not known, then percent will return NaN.
 * Return of a negative number indicates tracking is not in progress.
 * **/
public interface ProgressReporter {
	/**Indicate a certain number of expected steps have been taken.**/
	public void update(long delta);
	
	/**Percentage of expected steps that have been seen.**/
	public double percent();
	
	/**Set how many steps are expected; also clears the count.**/
	public void reset(long expected);
	
	/**At a best-effort, how often should reports be made?
	 * If set to zero or less, then any unit will do.
	 * **/
	public long reportStep();
	
	/**Get a status message.  
	 * null is preferred for signaling "no message".**/
	public String message();
	public void message(String message);
	
	
	/**Dummy progress recorder.  Always returns -1 for status inquiries.**/
	public static final class NOP implements ProgressReporter {
		public NOP() {}

		@Override public void update(long delta) {}
		@Override public void reset(long expected) {}
		@Override public double percent() {return -1;}
		@Override public long reportStep() {return -1;}
		@Override public String message() {return "";}
		@Override public void message(String message) {}		
	}
	
	/**Thread-safe progress reporter for.**/
	public static final class Counter implements ProgressReporter {
		private final AtomicLong counter = new AtomicLong();
		private long expected=1;
		private final long reportStep;
		private String message;
		
		public Counter(long reportStep) {this.reportStep = reportStep;}

		@Override public void update(long delta) {counter.addAndGet(delta);}
		@Override public void reset(long expected) {this.expected = expected; counter.set(0); message = null;}
		@Override public long reportStep() {return reportStep;}
		@Override public String message() {return message;}
		@Override public void message(String message) {this.message = message;}
		
		public double percent() {
			if (expected ==0) {return Double.NaN;}
			if (expected <0) {return -1;}
			return counter.intValue()/((double) expected);
		}
	}
}