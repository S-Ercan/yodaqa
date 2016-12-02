package cz.brmlab.yodaqa.analysis.question;

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

public class AlpinoParser extends JCasAnnotator_ImplBase {

	private AlpinoConstituentAnnotator constituentAnnotator;
	private AlpinoDependencyAnnotator dependencyAnnotator;

	@Override
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
	}

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
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

	private void annotateConstituents(JCas jCas, List<Token> tokenList) {
		constituentAnnotator = new AlpinoConstituentAnnotator();
		String output = constituentAnnotator.process(jCas, tokenList);
		if (output != null && !output.equals("")) {
			constituentAnnotator.processAlpinoOutput(jCas, tokenList, output);
		}
	}

	private void annotateDependencies(JCas jCas, List<Token> tokenList) {
		dependencyAnnotator = new AlpinoDependencyAnnotator();
		String output = dependencyAnnotator.process(jCas, tokenList);
		if (output != null && !output.equals("")) {
			dependencyAnnotator.processAlpinoOutput(jCas, tokenList, output);
		}
	}

}
