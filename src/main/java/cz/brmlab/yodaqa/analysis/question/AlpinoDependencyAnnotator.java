package cz.brmlab.yodaqa.analysis.question;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.cas.Type;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;
import java.util.Iterator;
import java.util.Map;

public class AlpinoDependencyAnnotator extends AlpinoAnnotator {

	private static AlpinoDependencyAnnotator dependencyAnnotator = null;

	public static AlpinoDependencyAnnotator getAlpinoDependencyAnnotator(int numPassages) {
		if (dependencyAnnotator == null) {
			dependencyAnnotator = new AlpinoDependencyAnnotator(numPassages);
		}
		return dependencyAnnotator;
	}

	private AlpinoDependencyAnnotator(int numPassages) {
		setNumPassages(numPassages);
	}

	@Override
	protected ProcessBuilder createAlpinoProcess() {
		return new ProcessBuilder("/bin/bash", "bin/Alpino", "end_hook=triples", "-parse", "-notk");
	}

	@Override
	protected void processAlpinoOutput(String output) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public List<Dependency> processDependencyTriples(String output) {
		ArrayList<Dependency> dependencies = new ArrayList<>();
		if (output == null || output.equals("")) {
			System.out.println("No dependency triples received");
			return dependencies;
		}

		Iterator iterator = getTokenListByJCas().entrySet().iterator();
		for (String triple : output.split("\n")) {
			Map.Entry<JCas, List<Token>> entry = (Map.Entry<JCas, List<Token>>) iterator.next();
			JCas aJCas = entry.getKey();
			List<Token> aTokenList = entry.getValue();

			Pattern pattern = Pattern.compile("(.+)[\\|](.+)[\\|](.+)[\\|]");
			Matcher matcher = pattern.matcher(triple);
			if (matcher.find()) {
				String governorString = matcher.group(1);
				if (governorString.equals("top/top")) {
					continue;
				}
				String aDependencyType = matcher.group(2).split("/")[1];
				String dependentString = matcher.group(3);
				String dependencyTypeName = dependencyPackage + "." + aDependencyType.toUpperCase();

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

				Token governor = aTokenList.get(governorIndex);
				Token dependent = aTokenList.get(dependentIndex);

				Dependency dep = (Dependency) aJCas.getCas().createFS(type);
				dep.setDependencyType(aDependencyType);
				dep.setGovernor(governor);
				dep.setDependent(dependent);
				dep.setBegin(dependent.getBegin());
				dep.setEnd(dependent.getEnd());
				dep.addToIndexes();
				dependencies.add(dep);
			}
		}
		return dependencies;
	}
}
