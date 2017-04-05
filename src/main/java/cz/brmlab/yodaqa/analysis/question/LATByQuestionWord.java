package cz.brmlab.yodaqa.analysis.question;

import cz.brmlab.yodaqa.flow.dashboard.QuestionDashboard;
import cz.brmlab.yodaqa.model.TyCor.ConfirmationQuestionLAT;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.brmlab.yodaqa.model.TyCor.LAT;
import cz.brmlab.yodaqa.model.TyCor.QuestionWordLAT;
import cz.brmlab.yodaqa.model.alpino.type.constituent.SV1;
import cz.brmlab.yodaqa.model.alpino.type.dependency.WHD;
import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;

/**
 * Generate LAT annotations in a QuestionCAS. These are words that should be type-coercable to the
 * answer term. E.g. "Who starred in Moon?" should generate LATs "who", "actor", possibly "star".
 * Candidate answers will be matched against LATs to acquire score. Focus is typically always also
 * an LAT.
 */
public class LATByQuestionWord extends JCasAnnotator_ImplBase {

	final Logger logger = LoggerFactory.getLogger(LATByQuestionWord.class);

	@Override
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		boolean isWHQuestion = false;
		for (WHD whd : JCasUtil.select(jcas, WHD.class)) {
			addWHDLAT(jcas, whd);
			isWHQuestion = true;
		}
		if (!isWHQuestion) {
			for (SV1 sv1 : JCasUtil.select(jcas, SV1.class)) {
				String sv1Type = "";
				for (Token token : JCasUtil.selectCovered(Token.class, sv1)) {
					if (token.getLemma().getValue().equals("kunnen")) {
						sv1Type = "capability";
					}
				}
				addSV1LAT(jcas, sv1, sv1Type);
			}
		}
	}

	protected void addSV1LAT(JCas jcas, SV1 sv1, String sv1Type) {
		QuestionDashboard.getInstance().setIsConfirmationQuestion(true);
		addLAT(new ConfirmationQuestionLAT(jcas), sv1.getBegin(), sv1.getEnd(), sv1, sv1Type, null,
				1, 0.0);
	}

	/**
	 *
	 * @param jcas
	 * @param whd
	 */
	protected void addWHDLAT(JCas jcas, WHD whd) {
		String text = whd.getGovernor().getCoveredText().toLowerCase();

		switch (text) {
			case "wie":
				/* (6833){00007846} <noun.Tops>[03] S: (n) person#1 (person%1:03:00::), individual#1 (individual%1:03:00::), someone#1 (someone%1:03:00::), somebody#1 (somebody%1:03:00::), mortal#1 (mortal%1:03:00::), soul#2 (soul%1:03:00::) (a human being) "there was too much for oneperson to do" */
				addWHDLAT(jcas, whd, "person", null, 7846, 0.0, new QuestionWordLAT(jcas));
				break;
			case "wanneer":
				/* (114){15147173} <noun.time>[28] S: (n) time#3 (time%1:28:00::) (an indefinite period (usually marked by specific attributes or activities)) "the time of year for planting"; "he was a great actor in his time" */
				addWHDLAT(jcas, whd, "time", null, 15147173, 0.0, new QuestionWordLAT(jcas));
				/* (23){15184543} <noun.time>[28] S: (n) date#1 (date%1:28:00::), day of the month#1 (day_of_the_month%1:28:00::) (the specified day of the month) "what is the date today?" */
				addWHDLAT(jcas, whd, "date", null, 15184543, 0.0, new QuestionWordLAT(jcas));
				break;
			case "waar":
				/* (992){00027365} <noun.Tops>[03] S: (n) location#1 (location%1:03:00::) (a point or extent in space) */
				addWHDLAT(jcas, whd, "location", null, 27365, 0.0, new QuestionWordLAT(jcas));
				break;
			case "hoeveel":
				/* (15){00033914} <noun.Tops>[03] S: (n) measure#2 (measure%1:03:00::), quantity#1 (quantity%1:03:00::), amount#3 (amount%1:03:00::) (how much there is or how many there are of something that you can quantify) */
				addWHDLAT(jcas, whd, "amount", null, 33914, 0.0, new QuestionWordLAT(jcas));
				break;
			case "waarvoor":
				// TODO: synset shouldn't be 1, but what should it be?
				addWHDLAT(jcas, whd, "purpose", null, 1, 0.0, new QuestionWordLAT(jcas));
				break;
			default:
				break;
		}
	}

	protected void addWHDLAT(JCas jcas, WHD whd, String text, POS pos, long synset, double spec,
			LAT lat) {
//		if (pos == null) {
//			/* We have a synthetic whd noun, synthetize
//			 * a POS tag for it. */
//			pos = new NN(jcas);
//			pos.setBegin(whd.getBegin());
//			pos.setEnd(whd.getEnd());
//			pos.setPosValue("NNS");
//			pos.addToIndexes();
//		}

		addLAT(lat, whd.getBegin(), whd.getEnd(), whd, text, pos, synset, spec);
	}

	protected void addLAT(LAT lat, int begin, int end, Annotation base, String text, POS pos,
			long synset, double spec) {
		lat.setBegin(begin);
		lat.setEnd(end);
		lat.setBase(base);
		lat.setPos(pos);
		lat.setText(text);
		lat.setSpecificity(spec);
		lat.setSynset(synset);
		lat.addToIndexes();
		logger.debug("new LAT by {}: <<{}>>/{}", base.getType().getShortName(), text, synset);
	}
}
