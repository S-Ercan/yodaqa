package cz.brmlab.yodaqa.flow.dashboard;

import cz.brmlab.yodaqa.analysis.question.AlpinoConstituentAnnotator;
import cz.brmlab.yodaqa.analysis.question.AlpinoDependencyAnnotator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnswerDashboard {

	private static AnswerDashboard answerDashboard = new AnswerDashboard();

	private int numSearchResults = 0;
	private int numSearchResultsProcessed = 0;

	private List<String> sentences = new ArrayList<>();
	private Map<String, String> sentenceToParseTree = new HashMap<>();
	private Map<String, String> sentenceToDependencyTriples = new HashMap<>();

	private Object flag = new Object();

	public Object getFlag() {
		return flag;
	}

	public void setFlag(Object flag) {
		this.flag = flag;
	}

	private AnswerDashboard() {
	}

	public static AnswerDashboard getAnswerDashBoard() {
		if (answerDashboard == null) {
			answerDashboard = new AnswerDashboard();
		}
		return answerDashboard;
	}

	public synchronized void addSentence(String sentence) {
		sentences.add(sentence);
		sentenceToParseTree.put(sentence, "");
		sentenceToDependencyTriples.put(sentence, "");
	}

	public synchronized void getAlpinoOutput() {
		String input = "";
		for (String sentence : sentences) {
			input += sentence + '\n';
		}
		if (input.equals("")) {
			return;
		}

		AlpinoConstituentAnnotator constituentAnnotator = new AlpinoConstituentAnnotator();
		AlpinoDependencyAnnotator dependencyAnnotator = new AlpinoDependencyAnnotator();
		System.out.println("Start parsing");
		String parseOutput = constituentAnnotator.process(input);
		System.out.println("End parsing, start triples");
		String triplesOutput = dependencyAnnotator.process(input);
		System.out.println("End triples");
		processParseOutput(parseOutput);
		processTriplesOutput(triplesOutput);
	}

	private void processParseOutput(String parseOutput) {
		String[] strings = parseOutput.split("<\\?xml version=\"1.0\" encoding=\"UTF-8\"\\?>");
		int counter = 0;
		for (String string : strings) {
			if (string.equals("")) {
				continue;
			}
			sentenceToParseTree.put(sentences.get(counter), string.replaceAll("\\n", ""));
			counter++;
		}
	}

	private void processTriplesOutput(String triplesOutput) {
		String[] strings = triplesOutput.split("\n");
		String sentenceTriples = "";
		int lastSentenceNumber = 1;
		int sentenceNumber;
		for (String string : strings) {
			Pattern digitPattern = Pattern.compile(".+\\|(\\d+)");
			Matcher digitMatcher = digitPattern.matcher(string);
			digitMatcher.find();
			sentenceNumber = Integer.valueOf(digitMatcher.group(1));

			if (lastSentenceNumber != sentenceNumber) {
				sentenceToDependencyTriples.put(sentences.get(lastSentenceNumber - 1),
						sentenceTriples);
				sentenceTriples = "";
				lastSentenceNumber = sentenceNumber;
			}

			sentenceTriples += string + "\n";
		}
		sentenceToDependencyTriples.put(sentences.get(lastSentenceNumber - 1), sentenceTriples);
	}

	public boolean outputsPresentForSentence(String sentence) {
		String parseTree = sentenceToParseTree.get(sentence);
		String triples = sentenceToDependencyTriples.get(sentence);
		if (parseTree != null && triples != null) {
			if (!parseTree.equals("") && !triples.equals("")) {
				System.out.println();
			}
			return !parseTree.equals("") && !triples.equals("");
		}
		return false;
	}

	public int getNumSearchResults() {
		return numSearchResults;
	}

	public synchronized void setNumSearchResults(int numSearchResults) {
		this.numSearchResults += numSearchResults;
	}

	public int getNumSearchResultsProcessed() {
		return numSearchResultsProcessed;
	}

	public synchronized void incrementNumSearchResultsProcessed() {
		this.numSearchResultsProcessed++;
//		if (sentences.size() == numSearchResults || sentences.size() >= 100) {
		if (numSearchResultsProcessed == numSearchResults) {
//			getAlpinoOutput();
		}
	}

	public String getParseTreeForSentence(String sentence) {
		return sentenceToParseTree.get(sentence);
	}

	public void removeParseTreeForSentence(String sentence) {
		sentenceToParseTree.remove(sentence);
	}

	public String getDependencyTriplesForSentence(String sentence) {
		return sentenceToDependencyTriples.get(sentence);
	}

	public void removeDependencyTriplesForSentence(String sentence) {
		sentenceToDependencyTriples.remove(sentence);
	}
}
