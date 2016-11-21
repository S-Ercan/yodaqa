package cz.brmlab.yodaqa.analysis.question;

import cz.brmlab.yodaqa.model.PickedPassage.PickedPassageInfo;
import java.util.ArrayList;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import java.util.Iterator;

public class AlpinoParser extends JCasAnnotator_ImplBase {

	private AlpinoConstituentAnnotator constituentAnnotator;
	private AlpinoDependencyAnnotator dependencyAnnotator;

	@Override
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
	}

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {

		int numPassages = getNumberOfPassagesToAnalyze(aJCas);
		FSIterator<Annotation> typeToParseIterator = aJCas.getAnnotationIndex(JCasUtil.
				getType(aJCas, Sentence.class)).iterator();

		while (typeToParseIterator.hasNext()) {
			Annotation annotation = typeToParseIterator.next();
			ArrayList<Token> tokenList = new ArrayList<>();
			for (Token token : JCasUtil.selectCovered(Token.class, annotation)) {
				tokenList.add(token);
			}
			annotateConstituents(numPassages, aJCas, tokenList);
			annotateDependencies(numPassages, aJCas, tokenList);
		}
	}

	private int getNumberOfPassagesToAnalyze(JCas aJCas) {
		Iterator<PickedPassageInfo> ppInfoIterator
				= JCasUtil.select(aJCas, PickedPassageInfo.class).iterator();
		int numPassages = 0;
		while (ppInfoIterator.hasNext()) {
			numPassages += ppInfoIterator.next().getNumPassages();
		}
		return numPassages;
	}

	private void annotateConstituents(int numPassages, JCas jCas, List<Token> tokenList) {
		constituentAnnotator = AlpinoConstituentAnnotator.getAlpinoConstituentAnnotator(
				numPassages);
		constituentAnnotator.process(jCas, tokenList);
	}

	private void annotateDependencies(int numPassages, JCas jCas, List<Token> tokenList) {
		dependencyAnnotator = AlpinoDependencyAnnotator.getAlpinoDependencyAnnotator(
				numPassages);
		dependencyAnnotator.process(jCas, tokenList);
	}

}
