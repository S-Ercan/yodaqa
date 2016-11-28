package cz.brmlab.yodaqa.analysis.question;

import cz.brmlab.yodaqa.flow.dashboard.AnswerDashboard;
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
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.CASRuntimeException;

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
			JCasUtil.selectCovered(Token.class, annotation).
					forEach((token) -> {
						tokenList.add(token);
					}
					);
			annotateConstituents(aJCas, tokenList);
			annotateDependencies(aJCas, tokenList);
		}
	}

	private int getNumberOfPassagesToAnalyze(JCas aJCas) {
		int numPassages = 0;
		try {
			aJCas.getView("PickedPassages");
			numPassages = AnswerDashboard.getAnswerDashBoard().getNumSearchResults();
		} catch (CASException | CASRuntimeException e) {
			try {
				aJCas.getView("_InitialView");
				numPassages = 1;
			} catch (CASException ex1) {

			}
		}

		return numPassages;
	}

	private void annotateConstituents(JCas jCas, List<Token> tokenList) {
		constituentAnnotator = AlpinoConstituentAnnotator.getAlpinoConstituentAnnotator();
//		constituentAnnotator.process(tokenList);
	}

	private void annotateDependencies(JCas jCas, List<Token> tokenList) {
		dependencyAnnotator = AlpinoDependencyAnnotator.getAlpinoDependencyAnnotator();
//		dependencyAnnotator.process(tokenList);
	}

}
