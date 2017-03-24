package cz.brmlab.yodaqa.analysis.passage;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.descriptor.SofaCapability;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.LoggerFactory;

import cz.brmlab.yodaqa.analysis.ansscore.AnswerFV;
import cz.brmlab.yodaqa.analysis.TreeUtil;
import cz.brmlab.yodaqa.analysis.ansscore.AF;
import cz.brmlab.yodaqa.model.SearchResult.Passage;
import cz.brmlab.yodaqa.model.SearchResult.ResultInfo;
import cz.brmlab.yodaqa.model.TyCor.LAT;
import cz.brmlab.yodaqa.model.alpino.type.dependency.LD;
import cz.brmlab.yodaqa.model.alpino.type.dependency.ME;
import cz.brmlab.yodaqa.model.alpino.type.dependency.MOD;
import cz.brmlab.yodaqa.model.alpino.type.dependency.OBJ1;
import cz.brmlab.yodaqa.model.alpino.type.dependency.OBJ2;
import cz.brmlab.yodaqa.model.alpino.type.dependency.PC;
import cz.brmlab.yodaqa.model.alpino.type.dependency.PREDC;
import cz.brmlab.yodaqa.model.alpino.type.dependency.SU;
import cz.brmlab.yodaqa.model.alpino.type.dependency.WHD;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;
import org.apache.uima.jcas.cas.TOP;

/**
 * Create CandidateAnswer for the largest NP covering governor of NSUBJ where the dependent is the
 * question LAT.
 *
 * In human language, the focus of question "What is the critical mass of plutonium?" is "mass"; in
 * passage "A bare critical mass of plutonium at normal density is roughly 10 kilograms.", we find
 * out that NSUBJ dependent is "mass", so we look at the governor ("kilograms") and take the largest
 * covering NP ("roughly 10 kilograms").
 *
 * Originally, we thought of this as focus matching, but (sp=0) LAT is better as we do desirable
 * transformations like who->person or hot->temperature when generating these, and they *are* focus
 * based.
 *
 * Of course this is a special case of CanByNPSurprise but we should be subduing those.
 */
@SofaCapability(
		inputSofas = {"Question", "Result", "PickedPassages"},
		outputSofas = {"PickedPassages"}
)

public class CanByLATSubject extends CandidateGenerator {

	public CanByLATSubject() {
		logger = LoggerFactory.getLogger(CanByLATSubject.class);
	}

	@Override
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		JCas questionView, resultView, passagesView;
		try {
			questionView = jcas.getView("Question");
			resultView = jcas.getView("Result");
			passagesView = jcas.getView("PickedPassages");
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		ResultInfo ri = JCasUtil.selectSingle(resultView, ResultInfo.class);
		for (SU subj : JCasUtil.select(passagesView, SU.class)) {
			processSubject(questionView, passagesView, ri, subj);
		}
	}

	protected void processSubject(JCas questionView, JCas passagesView, ResultInfo ri,
			Dependency nsubj) throws AnalysisEngineProcessException {
		Passage passage = JCasUtil.selectCovering(Passage.class, nsubj).get(0);
		String subjlemma = nsubj.getDependent().getLemma().getValue().toLowerCase();

		LAT questionLat = null;
		for (LAT l : JCasUtil.select(questionView, LAT.class)) {
			if (l.getBase() instanceof WHD) {
				questionLat = l;
				break;
			}
		}

		for (LAT l : JCasUtil.select(questionView, LAT.class)) {
			if (l.getBase() instanceof WHD) {
				continue;
			}
			/* We consider only the primary LATs, and -1 sp LATs
			 * as those are the possible noun forms. TODO: tune */
			if (l.getSpecificity() < -1.0) {
				continue;
			}

			String latlemma = l.getText();
			// logger.debug("lat >{}< subj >{}<", latlemma, subjlemma);
			if (subjlemma.equals(latlemma)) {
				logger.debug("Passage subject {} matches question lat {}", subjlemma, latlemma);

				Annotation governor = TreeUtil.widestCoveringNP(nsubj.getGovernor());
				Annotation base = null;
				if (governor == null) {
					governor = nsubj.getGovernor(); // cheat :)
				}
				if (governor instanceof Token) {
					String pos = ((Token) governor).getPos().getPosValue();
					if (questionLat != null && (pos.matches("^v.*") || pos.matches("^V.*"))) {
						String text = questionLat.getText();
						// TODO: also handle the reverse case (i.e., for depObj.getGovernor())
						switch (text) {
							case "location":
								for (LD depObj : JCasUtil.selectCovered(LD.class, passage)) {
									// TODO: for this and all subsequent similar checks, requiring
									// a high WordNet score instead of an exact match is a better
									// approach
									if (depObj.getGovernor().equals(nsubj.getGovernor())) {
										base = TreeUtil.widestCoveringSubphrase(depObj.
												getDependent());
									}
								}
								break;
							case "amount":
								for (ME depObj : JCasUtil.selectCovered(ME.class, passage)) {
									if (depObj.getGovernor().equals(nsubj.getGovernor())) {
										base = TreeUtil.widestCoveringSubphrase(depObj.
												getDependent());
									}
								}
								break;
							case "person":
								for (PREDC depObj : JCasUtil.selectCovered(PREDC.class, passage)) {
									if (depObj.getGovernor().equals(nsubj.getGovernor())) {
										base = TreeUtil.widestCoveringSubphrase(depObj.
												getDependent());
									}
								}
								break;
							case "purpose":
								for (MOD depObj : JCasUtil.selectCovered(MOD.class, passage)) {
									if (depObj.getGovernor().equals(nsubj.getGovernor())) {
										base = TreeUtil.widestCoveringSubphrase(depObj.
												getDependent());
									}
								}
								for (PC depObj : JCasUtil.selectCovered(PC.class, passage)) {
									if (depObj.getGovernor().equals(nsubj.getGovernor())) {
										base = TreeUtil.widestCoveringSubphrase(depObj.
												getDependent());
									}
								}
								break;
							default:
								break;
						}
					}
					for (TOP anno : JCasUtil.selectAll(passagesView)) {
						String annoText = anno.toString();
//						System.out.println(annoText);
					}
					if (base == null) {
						for (PREDC depObj : JCasUtil.selectCovered(PREDC.class, passage)) {
							if (depObj.getGovernor().equals(nsubj.getGovernor())) {
								base = TreeUtil.widestCoveringSubphrase(depObj.getDependent());
							}
						}
					}
					if (base == null) {
						for (PC depObj : JCasUtil.selectCovered(PC.class, passage)) {
							if (depObj.getGovernor().equals(nsubj.getGovernor())) {
								base = TreeUtil.widestCoveringSubphrase(depObj.getDependent());
							}
						}
					}
					if (base == null) {
						for (OBJ1 depObj : JCasUtil.selectCovered(OBJ1.class, passage)) {
							if (depObj.getGovernor().equals(nsubj.getGovernor())) {
								base = TreeUtil.widestCoveringSubphrase(depObj.getDependent());
							}
						}
					}
					if (base == null) {
						for (OBJ2 depObj : JCasUtil.selectCovered(OBJ2.class, passage)) {
							if (depObj.getGovernor().equals(nsubj.getGovernor())) {
								base = TreeUtil.widestCoveringSubphrase(depObj.getDependent());
							}
						}
					}
//					if (base == null) {
//						continue;
//					}
				}
				if (base == null) {
//						logger.debug("Ignoring verb governor {} {}", pos, base.getCoveredText());
					continue;
				}

				AnswerFV fv = new AnswerFV(ri.getAnsfeatures());
				fv.merge(new AnswerFV(passage.getAnsfeatures()));
				/* This is both origin and tycor feature, essentially. */
				fv.setFeature(AF.OriginPsgNPByLATSubj, 1.0);

				addCandidateAnswer(passagesView, passage, base, fv);
			}
		}
	}
}
