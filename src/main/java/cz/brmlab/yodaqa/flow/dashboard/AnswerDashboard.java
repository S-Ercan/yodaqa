package cz.brmlab.yodaqa.flow.dashboard;

import cz.brmlab.yodaqa.analysis.question.AlpinoConstituentAnnotator;
import cz.brmlab.yodaqa.analysis.question.AlpinoDependencyAnnotator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AnswerDashboard {

	private static AnswerDashboard answerDashboard;

	private int numSearchResults;
	private List<String> sentences;
	private Map<String, String> sentenceToParseTree;
	private Map<String, String> sentenceToDependencyTriples;

	private AnswerDashboard() {
		numSearchResults = 0;
		sentences = new ArrayList<>();
		sentenceToParseTree = new HashMap<>();
		sentenceToDependencyTriples = new HashMap<>();
	}

	public static AnswerDashboard getAnswerDashBoard() {
		if (answerDashboard == null) {
			answerDashboard = new AnswerDashboard();
		}
		return answerDashboard;
	}

	public synchronized int getNumSearchResults() {
		return numSearchResults;
	}

	public synchronized void setNumSearchResults(int numPickedPassages) {
		numSearchResults += numPickedPassages;
	}

	public synchronized void incrementNumSearchResults(int increment) {
		numSearchResults += increment;
	}

	public synchronized Map<String, String> getSentenceToParseTree() {
		return sentenceToParseTree;
	}

	public synchronized void addSentence(String sentence) {
		sentences.add(sentence);
		if (sentences.size() == numSearchResults) {
			getAlpinoOutput();
		}
	}

	private void getAlpinoOutput() {
		AlpinoConstituentAnnotator constituentAnnotator = AlpinoConstituentAnnotator.
				getAlpinoConstituentAnnotator();
		AlpinoDependencyAnnotator dependencyAnnotator = AlpinoDependencyAnnotator.
				getAlpinoDependencyAnnotator();
		String input = "";
		for (String sentence : sentences) {
			input += sentence;
		}
		constituentAnnotator.process(input);
		dependencyAnnotator.process(input);
	}

}
