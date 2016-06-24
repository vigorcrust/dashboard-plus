/*
 ===========================================================================
 @    $Author$
 @  $Revision$
 @      $Date$
 @
 ===========================================================================
 */
package com.baloise.confluence.dashboardplus.neo4jteststatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.apache.commons.lang.StringUtils;

import com.atlassian.json.jsonorg.JSONArray;
import com.atlassian.json.jsonorg.JSONObject;

/**
 * 
 */
public class TestResultEvaluation {

	public enum Status {
		error, red, yellow, green, grey, unknown, orange
	}

	public static class TestResult {
		public Status status = Status.unknown;
		public Throwable error = null;
		public Vector<String> runs = new Vector<String>();
		public long totalPassed = 0;
		public long totalFailed = 0;
		public long totalSkipped = 0;
		public long totalIgnored = 0;

		public TestResult() {
		}

		public TestResult(List<String> runs, Status status, long totalPassed,
				long totalFailed, long totalSkipped, long totalIgnored) {
			this.status = status;
			this.totalPassed = totalPassed;
			this.totalFailed = totalFailed;
			this.totalSkipped = totalSkipped;
			this.totalIgnored = totalIgnored;
			if (runs != null) {
				this.runs.addAll(runs);
			}
		}

		public TestResult(Status status, Throwable e) {
			this(null, status, 0, 0, 0, 0);
			error = e;
		}

		@Override
		public String toString() {
			return "Status: " + status + ", passed: " + totalPassed
					+ ", failed: " + totalFailed + ", skipped: " + totalSkipped
					+ ", ignored: " + totalIgnored;
		}

	}

	private static TestResult error(Throwable e) {
		return new TestResult(Status.error, e);
	}

	public static void main(String[] args) {
		long time = System.currentTimeMillis();
		System.out.println(new TestResultEvaluation("jirafilter_93607,local",
				"teststage=unknown,service=unknown", 0.8, 0.3).asTestResult());
		System.out.println(System.currentTimeMillis() - time);
	}

	public Vector<String> runs = new Vector<String>();

	private String[] runnames;
	private String[] criterias;

	private double redIfEqualOrBelow;

	private double greenIfEqualOrAbove;

	public long totalPassed = 0;;
	public long totalFailed = 0;
	public long totalSkipped = 0;
	public long totalIgnored = 0;

	private Exception exception = null;
	private List<String> cypher;

	public TestResultEvaluation(String runnames, String criterias,
			double greenIfEqualOrAbove, double redIfEqualOrBelow) {
		this.runnames = asStringArrayWithoutEmptyEntries(runnames);
		this.criterias = asStringArrayWithoutEmptyEntries(criterias);
		this.redIfEqualOrBelow = redIfEqualOrBelow;
		this.greenIfEqualOrAbove = greenIfEqualOrAbove;
	}

	private String[] asStringArrayWithoutEmptyEntries(String entries) {
		if (entries == null) {
			return new String[] {};
		}
		String[] splittedEntries = entries.split(",");
		int count = 0;
		for (int i = 0; i < splittedEntries.length; i++) {
			splittedEntries[i] = splittedEntries[i].trim();
			if (!splittedEntries[i].isEmpty()) {
				count++;
			}
		}
		String[] result = new String[count];
		count = 0;
		for (int i = 0; i < splittedEntries.length; i++) {
			if (!splittedEntries[i].isEmpty()) {
				result[count] = splittedEntries[i];
				count++;
			}
		}
		return result;
	}

	public TestResult asTestResult() {
		calculateTotals();
		return calculateTestResult();
	}

	public TestResult calculateTestResult() {
		if (exception != null) {
			return error(exception);
		}
		if (totalPassed < 0 | totalFailed < 0 | totalSkipped < 0) {
			return error(new java.lang.AssertionError(
					"Some total value is less than 0"));
		}
		if (totalPassed == 0 & totalFailed == 0 & totalSkipped == 0) {
			return new TestResult();
		}
		if (totalPassed == 0 & totalFailed == 0) {
			return error(new java.lang.AssertionError(
					"Some total value does equal 0"));
		}
		Double passRate = new Double(totalPassed)
				/ (new Double(totalFailed) + totalPassed);
		if (passRate >= greenIfEqualOrAbove) {
			return new TestResult(runs, Status.green, totalPassed, totalFailed,
					totalSkipped, totalIgnored);
		}
		if (passRate > redIfEqualOrBelow) {
			return new TestResult(runs, Status.yellow, totalPassed,
					totalFailed, totalSkipped, totalIgnored);
		}
		if (passRate <= redIfEqualOrBelow) {
			return new TestResult(runs, Status.red, totalPassed, totalFailed,
					totalSkipped, totalIgnored);
		}
		return error(new IllegalArgumentException("Evaluated pass rate = "
				+ passRate));
	}

	public void calculateTotals() {
		Neo4JConnector neo4jConnector = new Neo4JConnector();
		totalPassed = 0;
		totalFailed = 0;
		totalSkipped = 0;
		totalIgnored = 0;
		try {
			runs = new Vector<String>();
			String joinedRunnames = StringUtils.join(runnames, "', '");

			cypher = new ArrayList<String>();
			cypher.add("MATCH (tre:TestResult)<--(tru:TestRun)");
			cypher.add("WHERE tru.runname IN ['" + joinedRunnames + "']");
			int i = 0;
			for (String criteria : criterias) {
				try {
					String[] splittedCriteria = criteria.split("=");
					cypher.add("MATCH tre-->(tt" + i + ":TestTag {key: '"
							+ splittedCriteria[0] + "', value: '"
							+ splittedCriteria[1] + "'})");
				} catch (Exception e) {
				}
				i++;
			}
			cypher.add("RETURN tre.result, count(tre)");
			String result = neo4jConnector.getResult(getCypher());
			JSONArray data = new JSONObject(result).getJSONArray("data");

			for (int index = 0; index < data.length(); index++) {
				String testresult = data.getJSONArray(index).getString(0);
				int total = data.getJSONArray(index).getInt(1);
				if ("passed".equalsIgnoreCase(testresult)) {
					totalPassed += total;
				}
				if ("failed".equalsIgnoreCase(testresult)) {
					totalFailed += total;
				}
				if ("ignored".equalsIgnoreCase(testresult)) {
					totalIgnored += total;
				}
				if ("skipped".equalsIgnoreCase(testresult)) {
					totalSkipped += total;
				}
			}
			//}
		} catch (Exception e) {
			e.printStackTrace();
			totalPassed = -1;
			totalFailed = -1;
			totalSkipped = -1;
			totalIgnored = -1;
			exception = e;
		}
	}

	public String getCypher() {
		if (cypher == null)
			return null;
		String cypherString = "";
		for (String c : cypher) {
			cypherString = cypherString + " " + c;
		}
		return cypherString;
	}

}
