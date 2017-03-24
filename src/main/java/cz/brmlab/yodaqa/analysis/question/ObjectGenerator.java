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

import cz.brmlab.yodaqa.analysis.TreeUtil;
import cz.brmlab.yodaqa.analysis.answer.SyntaxCanonization;
import cz.brmlab.yodaqa.model.Question.Subject;
import cz.brmlab.yodaqa.model.alpino.type.constituent.NP;
import cz.brmlab.yodaqa.model.alpino.type.constituent.REL;
import cz.brmlab.yodaqa.model.alpino.type.constituent.SV1;
import cz.brmlab.yodaqa.model.alpino.type.dependency.OBJ1;
import cz.brmlab.yodaqa.model.alpino.type.dependency.OBJ2;
import cz.brmlab.yodaqa.model.alpino.type.dependency.PREDC;
import cz.brmlab.yodaqa.model.alpino.type.dependency.SU;
import cz.brmlab.yodaqa.model.alpino.type.dependency.VC;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.Constituent;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.ROOT;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.WHNP;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;
import java.util.ArrayList;
import java.util.List;

/**
 * Subject annotations in a QuestionCAS. These represent key information stored in the question that
 * is then used in primary search.
 *
 * This generates clues from the question subject, i.e. NSUBJ annotation. E.g. in "When did Einstein
 * die?", subject is "Einstein" and will have such a clue generated.
 */
public class ObjectGenerator extends JCasAnnotator_ImplBase {

	final Logger logger = LoggerFactory.getLogger(SubjectGenerator.class);

	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
	}

	public void process(JCas jcas) throws AnalysisEngineProcessException {
		try {
			JCasUtil.selectSingle(jcas, SV1.class);
		} catch (IllegalArgumentException ex) {
			return;
		}
		for (ROOT sentence : JCasUtil.select(jcas, ROOT.class)) {
			processSentence(jcas, sentence);
		}
	}

	protected void processSentence(JCas jcas, Constituent sentence) throws
			AnalysisEngineProcessException {
		for (SU subj : JCasUtil.select(jcas, SU.class)) {
			processSubj(jcas, sentence, subj);
		}
	}

	protected void processSubj(JCas jcas, Constituent sentence, Dependency subj) throws
			AnalysisEngineProcessException {
		for (PREDC predc : JCasUtil.select(jcas, PREDC.class)) {
			String dependentText = predc.getDependent().getCoveredText();
		}
		for (OBJ1 obj1 : JCasUtil.select(jcas, OBJ1.class)) {
			String dependentText = obj1.getDependent().getCoveredText();
		}
		for (OBJ2 obj2 : JCasUtil.select(jcas, OBJ2.class)) {
			String dependentText = obj2.getDependent().getCoveredText();
		}
		for (VC vc : JCasUtil.select(jcas, VC.class)) {
			String dependentText = vc.getDependent().getCoveredText();
		}

		Token stok = subj.getDependent();
		Annotation parent = stok.getParent();
		Constituent cparent = null;
		if (parent != null && parent instanceof Constituent) {
			cparent = (Constituent) parent;
		}

		/* Skip question word focuses (e.g. "Who"). */
		if (stok.getPos().getPosValue().matches("^W.*")) {
			return;
		}
		/*
		 * In "What country is Berlin in?", "country" (with parent
		 * "What country" WHNP) is *also* a NSUBJ - skip that one.
		 */
		if (cparent instanceof WHNP) {
			return;
		}

		String genSubject = null;

		/* Prefer a covering Named Entity: */
		boolean genSubjectNE = false;
		for (NamedEntity ne : JCasUtil.selectCovering(NamedEntity.class, stok)) {
			addSubject(jcas, ne);
			genSubject = ne.getCoveredText();
			genSubjectNE = true;
		}

		/*
		 * N.B. Sometimes NamedEntity detection fails (e.g. "How high is Pikes
		 * peak?"). So when there's none, just add the token as the subject.
		 */
 /* But do not add subjects like "it". */
		if (genSubject == null && stok.getPos().getPosValue().matches(
				ClueByTokenConstituent.TOKENMATCH)) {
			addSubject(jcas, stok);
			genSubject = stok.getCoveredText();
		}
		/*
		 * However, just the token is often pretty useless, yielding e.g. - How
		 * hot does it get in Death Valley? (it) - What is the southwestern-most
		 * tip of England? (tip) - What is the capital of Laos? (capital) - What
		 * is the motto for California? (motto) - What is the name of the second
		 * Beatles album? (name) so we rather add the widest covering NP (e.g.
		 * "capital of Laos").
		 */
 /*
		 * (Adding just the token, e.g. "capital", above too also makes sense as
		 * it can be treated as reliable compared to the full phrase which may
		 * not be in the text word-by-word.)
		 */
		NP np = TreeUtil.widestCoveringAlpinoNP(stok);
		if (np == null) {
			// <<How long before bankruptcy is removed from a credit report?>>
			np = new NP(jcas, stok.getBegin(), stok.getEnd());
		} else if (np.getCoveredText().equals(genSubject)) {
			// <<it>> is often a NP too, or other short tokens
			return;
		}
		addSubject(jcas, np);

		List<REL> rels = new ArrayList(JCasUtil.select(jcas, REL.class));
		if (rels.size() == 1) {
			NP npWithoutRel = new NP(jcas, np.getBegin(), rels.get(0).getBegin() - 1);
			addSubject(jcas, npWithoutRel);
		}

		/*
		 * However, if there *is* a NamedEntity in the covering NP, add it as a
		 * subject too - NamedEntity subject clues can be treated as reliable.
		 */
		if (!genSubjectNE) {
			for (NamedEntity ne : JCasUtil.selectCovered(NamedEntity.class, np)) {
				addSubject(jcas, ne);
			}
		}

		/*
		 * Also generate subject for the shortest covering NP, which is often
		 * just a very specific phrase like 'main character' or 'middle name',
		 * useful as e.g. a property selector.
		 */
		NP npShort = TreeUtil.shortestCoveringAlpinoNP(stok);
		if (npShort != null && npShort != np && !npShort.getCoveredText().equals(genSubject)) {
			/*
			 * XXX: Blacklisting "name" in "the name of XYZ". We probably don't
			 * need a sophisticated name proxy like for LATs.
			 */
			if (!SyntaxCanonization.getCanonText(npShort.getCoveredText().toLowerCase()).equals(
					"name")) {
				addSubject(jcas, npShort);
			}
		}
	}

	protected void addSubject(JCas jcas, Annotation subj) throws AnalysisEngineProcessException {
		Subject s = new Subject(jcas);
		s.setBegin(subj.getBegin());
		s.setEnd(subj.getEnd());
		s.setBase(subj);
		s.addToIndexes();
	}
}
