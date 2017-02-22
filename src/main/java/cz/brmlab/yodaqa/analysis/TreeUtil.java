package cz.brmlab.yodaqa.analysis;

import cz.brmlab.yodaqa.model.alpino.type.constituent.MWU;
import cz.brmlab.yodaqa.model.alpino.type.constituent.NP;
import cz.brmlab.yodaqa.model.alpino.type.constituent.PP;
import java.util.LinkedList;
import java.util.List;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.Constituent;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;
import org.apache.uima.jcas.tcas.Annotation;

/**
 * Collection of dependency tree annotation tools. For example for iterating dependencies of a
 * specific token through prepositions.
 */
public class TreeUtil {

	public static List<Token> getAllGoverned(JCas jcas, Constituent sentence, Token gov,
			String typeMatch) {
		List<Token> list = new LinkedList<Token>();
		for (Dependency d : JCasUtil.selectCovered(Dependency.class, sentence)) {
			if (d.getGovernor() != gov) {
				continue;
			}
			if (d.getDependencyType().equals("prep") // dkpro < 1.7.0 "- of -"
					|| d.getDependencyType().equals("prep_of")) { // - of -
				if (d.getDependent().getLemma().getValue().toLowerCase().equals("of")) {
					list.addAll(getAllGoverned(jcas, sentence, d.getDependent(), typeMatch));
				} else {
					list.add(d.getDependent());
				}
			} else if (d.getDependencyType().matches(typeMatch)) {
				list.add(d.getDependent());
			} // else det: "the" name, amod: "last" name, ...
		}
		return list;
	}

	public static Annotation widestCoveringSubphrase(Token token) {
		Annotation widestSubphrase = null;

		for (NP currentAnnotation : JCasUtil.selectCovering(NP.class, token)) {
			if (widestSubphrase == null || widestSubphrase.getBegin() > currentAnnotation.
					getBegin() || widestSubphrase.getEnd() < currentAnnotation.getEnd()) {
				widestSubphrase = currentAnnotation;
			}
		}
		if (widestSubphrase == null) {
			for (PP currentAnnotation : JCasUtil.selectCovering(PP.class, token)) {
				if (widestSubphrase == null || widestSubphrase.getBegin() > currentAnnotation.
						getBegin() || widestSubphrase.getEnd() < currentAnnotation.getEnd()) {
					widestSubphrase = currentAnnotation;
				}
			}
		}
		if (widestSubphrase == null) {
			for (MWU currentAnnotation : JCasUtil.selectCovering(MWU.class, token)) {
				if (widestSubphrase == null || widestSubphrase.getBegin() > currentAnnotation.
						getBegin() || widestSubphrase.getEnd() < currentAnnotation.getEnd()) {
					widestSubphrase = currentAnnotation;
				}
			}
		}

		return widestSubphrase;
	}

	public static cz.brmlab.yodaqa.model.alpino.type.constituent.NP widestCoveringAlpinoNP(Token t) {
		cz.brmlab.yodaqa.model.alpino.type.constituent.NP bestnp = null;

		for (cz.brmlab.yodaqa.model.alpino.type.constituent.NP np : JCasUtil
				.selectCovering(cz.brmlab.yodaqa.model.alpino.type.constituent.NP.class, t)) {
			if (bestnp == null || bestnp.getBegin() > np.getBegin() || bestnp.getEnd() < np.getEnd()) {
				bestnp = np;
			}
		}

		return bestnp;
	}

	public static cz.brmlab.yodaqa.model.alpino.type.constituent.NP shortestCoveringAlpinoNP(Token t) {
		cz.brmlab.yodaqa.model.alpino.type.constituent.NP bestnp = null;

		for (cz.brmlab.yodaqa.model.alpino.type.constituent.NP np : JCasUtil
				.selectCovering(cz.brmlab.yodaqa.model.alpino.type.constituent.NP.class, t)) {
			if (bestnp == null || bestnp.getEnd() - bestnp.getBegin() > np.getEnd() - np.getBegin()) {
				bestnp = np;
			}
		}

		return bestnp;
	}

	public static NP widestCoveringNP(Token t) {
		NP bestnp = null;

		for (NP np : JCasUtil.selectCovering(NP.class, t)) {
			if (bestnp == null || bestnp.getBegin() > np.getBegin() || bestnp.getEnd() < np.getEnd()) {
				bestnp = np;
			}
		}

		return bestnp;
	}

	public static NP shortestCoveringNP(Token t) {
		NP bestnp = null;

		for (NP np : JCasUtil.selectCovering(NP.class, t)) {
			if (bestnp == null || bestnp.getEnd() - bestnp.getBegin() > np.getEnd() - np.getBegin()) {
				bestnp = np;
			}
		}

		return bestnp;
	}
}
