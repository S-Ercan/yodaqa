package cz.brmlab.yodaqa.analysis.rdf;

import cz.brmlab.yodaqa.provider.rdf.PropertyValue;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import vu.wntools.wnsimilarity.main.WordSim;
import vu.wntools.wordnet.WordnetData;
import vu.wntools.wordnet.WordnetLmfSaxParser;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.NullArgumentException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Counts probability of property containing a correct answer to given question. Uses SynonymsPCCP
 * to estimate similarity of (cooked) property to LAT.
 *
 * XXX: Method duplication with PropertyGloVeScoring.
 */
public class DutchWordnetPropertyScorer {

	private static DutchWordnetPropertyScorer propScorer;
	private static WordnetData wordnetData;

	final Logger logger = LoggerFactory.getLogger(DutchWordnetPropertyScorer.class);

	List<String> baseTexts;
	Map<String, Double> synonyms;
	// WordnetData wordnetData;

	public synchronized static DutchWordnetPropertyScorer getDutchWordnetPropertyScorer() {
		if (propScorer == null) {
			propScorer = new DutchWordnetPropertyScorer();
			return propScorer;
		} else {
			return new DutchWordnetPropertyScorer(wordnetData);
		}
	}

	private DutchWordnetPropertyScorer() {
		String odwnResources = System.getenv("ODWN_RESOURCES");
		if (odwnResources == null) {
			throw new NullArgumentException(
					"No \"ODWN_RESOURCES\" environment variable is specified.");
		}

		WordnetLmfSaxParser parser = new WordnetLmfSaxParser();
		String odwnResourcesPath = Paths.get(odwnResources, "odwn_orbn_gwg-LMF_1.3.xml").toString();
		parser.parseFile(odwnResourcesPath);
		wordnetData = parser.wordnetData;
	}

	private DutchWordnetPropertyScorer(WordnetData wordnetData) {
		DutchWordnetPropertyScorer.wordnetData = wordnetData;
	}

	protected void loadSynonyms(String text) {
		baseTexts.add(text);
		synonyms.put(text.toLowerCase(), 2.0);
	}

	protected Double getPropTextScore(String prop) {
		double maxSimilarityScore = 0.0;
		double similarityScore;
		for (String baseText : baseTexts) {
			similarityScore = WordSim.getWordSimLC(wordnetData, prop, baseText);
			if (similarityScore > maxSimilarityScore) {
				maxSimilarityScore = similarityScore;
			}
		}
		return maxSimilarityScore;
	}

	public Double getPropertyScore(JCas questionView, PropertyValue pv) {
		baseTexts = new ArrayList<>();
		synonyms = new HashMap<>();
		for (Token token : JCasUtil.select(questionView, Token.class)) {
			loadSynonyms(token.getCoveredText());
		}

		String prop = pv.getProperty().toLowerCase();
		prop = prop.replaceAll("[0-9]*$", "");

		if (!prop.contains(" ")) {
			return getPropTextScore(prop);
		} else {
			/*
			 * Multi-word property name, e.g. "český dabing" or
			 * "místo narození". XXX: be really silly about this for now, just
			 * trying to match the first and last word.
			 */
			String[] words = prop.split(" ");
			Double score = null;
			if (words.length == 0) {
				return score;
			}
			Double s0 = getPropTextScore(words[0]);
			if (s0 != null) {
				score = s0;
			}
			Double s1 = getPropTextScore(words[words.length - 1]);
			if (s1 != null) {
				if (score == null) {
					score = s1;
				} else {
					score += s1;
				}
			}
			return score;
		}
	}
}