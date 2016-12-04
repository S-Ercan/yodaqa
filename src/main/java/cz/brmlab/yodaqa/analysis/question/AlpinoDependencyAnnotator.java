package cz.brmlab.yodaqa.analysis.question;

import cz.brmlab.yodaqa.flow.dashboard.AnswerDashboard;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.cas.Type;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.CASRuntimeException;

public class AlpinoDependencyAnnotator extends AlpinoAnnotator {

	public String process(JCas jCas, List<Token> tokenList) {
		JCas ppView = null;
		try {
			ppView = jCas.getView("PickedPassages");
		} catch (CASRuntimeException ex) {
//			Logger.getLogger(AlpinoAnnotator.class.getName()).log(Level.SEVERE, null, ex);
		} catch (CASException ex) {
//			Logger.getLogger(AlpinoAnnotator.class.getName()).log(Level.SEVERE, null, ex);
		}
		String input = "";
		for (Token token : tokenList) {
			input += token.getCoveredText() + " ";
		}
		if (ppView != null) {
			String parseOutput = AnswerDashboard.getAnswerDashBoard().
					getDependencyTriplesForSentence(input);
			if (parseOutput.equals("")) {
				System.out.println();
			}
			return parseOutput;
		} else {
			return process(input);
		}
	}

	@Override
	protected ProcessBuilder createAlpinoProcess() {
		return new ProcessBuilder("/bin/bash", "bin/Alpino", "end_hook=triples", "-parse",
				"-notk");
	}

	@Override
	protected void processAlpinoOutput(JCas aJCas, List<Token> tokenList, String output) {
		if (output == null || output.equals("")) {
			System.out.println("No dependency triples received");
			return;
		}

		int currentSentenceNumber = 0;
		for (String triple : output.split("\n")) {
			Pattern pattern = Pattern.compile("(.+)[\\|](.+)[\\|](.+)[\\|](\\d+)");
			Matcher matcher = pattern.matcher(triple);
			if (matcher.find()) {
				String governorString = matcher.group(1);
				if (governorString.equals("top/top")) {
					continue;
				}
				String aDependencyType = matcher.group(2).split("/")[1];
				String dependentString = matcher.group(3);
				String dependencyTypeName = dependencyPackage + "." + aDependencyType.
						toUpperCase();

				int sentenceNumber = Integer.valueOf(matcher.group(4));
				if (currentSentenceNumber != sentenceNumber) {
					currentSentenceNumber = sentenceNumber;
				}

				Type type = aJCas.getTypeSystem().getType(dependencyTypeName);
				if (type == null) {
					type = JCasUtil.getType(aJCas, Dependency.class);
				}

				Pattern digitPattern = Pattern.compile(".+[\\[](\\d+)[,]");
				Matcher digitMatcher = digitPattern.matcher(governorString);
				digitMatcher.find();
				int governorIndex = Integer.valueOf(digitMatcher.group(1));
				digitMatcher = digitPattern.matcher(dependentString);
				digitMatcher.find();
				int dependentIndex = Integer.valueOf(digitMatcher.group(1));

				Token governor = tokenList.get(governorIndex);
				Token dependent = tokenList.get(dependentIndex);

				Dependency dep = (Dependency) aJCas.getCas().createFS(type);
				dep.setDependencyType(aDependencyType);
				dep.setGovernor(governor);
				dep.setDependent(dependent);
				dep.setBegin(dependent.getBegin());
				dep.setEnd(dependent.getEnd());
				dep.addToIndexes();
			}
		}
	}
}
