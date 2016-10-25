package cz.brmlab.yodaqa.analysis.question;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.brmlab.yodaqa.analysis.answer.SyntaxCanonization;
import cz.brmlab.yodaqa.model.Question.Clue;
import cz.brmlab.yodaqa.model.Question.ClueToken;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;

/**
 * Generate Clue annotations in a QuestionCAS. These represent key information
 * stored in the question that is then used in primary search.  E.g. "What was
 * the first book written by Terry Pratchett?" should generate clues "first",
 * "book", "first book", "write" and "Terry Pratchett".
 *
 * This one creates clues from a bunch of POS-interesting tokens (like nouns,
 * numbers, etc.) and also interesting constituents like noun phrases. */

public class ClueByTokenConstituent extends JCasAnnotator_ImplBase {
	final Logger logger = LoggerFactory.getLogger(ClueByTokenConstituent.class);

//	public static String TOKENMATCH = "CD|FW|JJ.*|NN.*|RB.*|UH.*";
	public static String TOKENMATCH = "N.*|NOUN.*";
	public static String CONSTITMATCH = "AD.*|NP|QP";

	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
	}

	public void process(JCas jcas) throws AnalysisEngineProcessException {
		/* Walk the constituent tree and add all content-bearing
		 * constituents as keyphrases and content-bearing tokens
		 * as keywords. The constituents are often nested, so
		 * we can add all of "the Nobel Prize for Physiology and Medicine",
		 * "the Nobel Prize", "Physiology and Medicine", etc.
		 * (And then the individual tokens too.) */

		/* This is a DFS over the Constituent tree. */
		for (Token t : JCasUtil.select(jcas, Token.class)) {
			if (t.getPos().getPosValue().matches(TOKENMATCH)) {
				if (t.getEnd() - t.getBegin() > 1) // skip one-letter things like interpunction
					addClue(new ClueToken(jcas), t.getBegin(), t.getEnd(), t, true, 1.0);
			}
		}
//		LinkedList<Constituent> lifo = new LinkedList<Constituent>();
//		for (ROOT sentence : JCasUtil.select(jcas, ROOT.class))
//			lifo.add(sentence);
//		while (!lifo.isEmpty()) {
//			Constituent c = lifo.poll();
//			if (c.getConstituentType().matches(CONSTITMATCH)) {
//				/* Sometimes, we get an absurd NP like "it".
//				 * Guard against that. */
//				boolean absurd = true;
//				for (Token t : JCasUtil.selectCovered(Token.class, c)) {
//					if (t.getPos().getPosValue().matches(TOKENMATCH)) {
//						absurd = false;
//						break;
//					}
//				}
//				if (absurd)
//					continue;
//
//				/* <1.0 so that we slightly prefer tokens,
//				 * usable even for fulltext search, when
//				 * merging clues. */
//				addClue(new CluePhrase(jcas), c.getBegin(), c.getEnd(), c, false, 0.99);
//			}
//
//			for (FeatureStructure child : c.getChildren().toArray()) {
//				if (!(child instanceof Constituent)) {
//					Token t = (Token) child;
//					if (t.getPos().getPosValue().matches(TOKENMATCH))
//						addClue(new ClueToken(jcas), t.getBegin(), t.getEnd(), t, true, 1.0);
//					continue;
//				}
//				lifo.add((Constituent) child);
//			}
//		}
	}

	protected void addClue(Clue clue, int begin, int end, Annotation base, boolean isReliable, double weight) {
		clue.setBegin(begin);
		clue.setEnd(end);
		clue.setBase(base);
		clue.setWeight(weight);
		clue.setLabel(SyntaxCanonization.getCanonText(clue.getCoveredText()));
		clue.setIsReliable(isReliable);
		clue.addToIndexes();
		logger.debug("new by {}: {}", base.getType().getShortName(), clue.getLabel());
	}
}
