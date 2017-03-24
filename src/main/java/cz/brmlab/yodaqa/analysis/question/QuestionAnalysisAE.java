package cz.brmlab.yodaqa.analysis.question;

import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.component.CasDumpWriter;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.brmlab.yodaqa.analysis.tycor.LATByWordnet;
import cz.brmlab.yodaqa.io.debug.DumpConstituents;
import cz.brmlab.yodaqa.provider.OpenNlpNamedEntities;
import de.tudarmstadt.ukp.dkpro.core.languagetool.LanguageToolSegmenter;

/**
 * Annotate the QuestionCAS.
 *
 * This is an aggregate AE that will run a variety of annotators on the QuestionCAS, preparing it
 * for the PrimarySearch and AnswerGenerator stages.
 */
public class QuestionAnalysisAE /* XXX: extends AggregateBuilder ? */ {

	final static Logger logger = LoggerFactory.getLogger(QuestionAnalysisAE.class);

	public static AnalysisEngineDescription createEngineDescription() throws
			ResourceInitializationException {
		AggregateBuilder builder = new AggregateBuilder();

		/*
		 * A bunch of DKpro-bound NLP processors (these are the giants we stand
		 * on the shoulders of)
		 */

 /* Token features: */
		builder.add(AnalysisEngineFactory.createEngineDescription(LanguageToolSegmenter.class,
				LanguageToolSegmenter.PARAM_LANGUAGE, "nl"));
		builder.add(AnalysisEngineFactory.createEngineDescription(AlpinoParser.class));

		/* Named Entities: */
		builder.add(OpenNlpNamedEntities.createEngineDescription());

		/* Okay! Now, we can proceed with our key tasks. */
		builder.add(AnalysisEngineFactory.createEngineDescription(FocusGenerator.class));
		builder.add(AnalysisEngineFactory.createEngineDescription(FocusNameProxy.class));
		builder.add(AnalysisEngineFactory.createEngineDescription(SubjectGenerator.class));
		builder.add(AnalysisEngineFactory.createEngineDescription(SVGenerator.class));
		builder.add(AnalysisEngineFactory.createEngineDescription(ObjectGenerator.class));

		/* Prepare LATs */
		builder.add(AnalysisEngineFactory.createEngineDescription(LATByQuestionWord.class));
		builder.add(AnalysisEngineFactory.createEngineDescription(LATByFocus.class));
		builder.add(AnalysisEngineFactory.createEngineDescription(LATBySV.class));
		/* Generalize imprecise LATs */
		builder.add(AnalysisEngineFactory.createEngineDescription(LATByWordnet.class,
				LATByWordnet.PARAM_EXPAND_SYNSET_LATS, false));

		/* Generate clues; the order is less specific to more specific */
		builder.add(AnalysisEngineFactory.createEngineDescription(ClueByTokenConstituent.class));
		builder.add(AnalysisEngineFactory.createEngineDescription(ClueBySV.class));
		builder.add(AnalysisEngineFactory.createEngineDescription(ClueByNE.class));
		builder.add(AnalysisEngineFactory.createEngineDescription(ClueByLAT.class));
		builder.add(AnalysisEngineFactory.createEngineDescription(ClueBySubject.class));
		/* Convert some syntactic clues to concept clues */
		builder.add(AnalysisEngineFactory.createEngineDescription(CluesToConcepts.class));
		/* Merge any duplicate clues */
		builder.add(AnalysisEngineFactory.createEngineDescription(CluesMergeByText.class));

		builder.add(AnalysisEngineFactory.createEngineDescription(DashboardHook.class));
		/* Classify question into classes */
		builder.add(AnalysisEngineFactory.createEngineDescription(ClassClassifier.class));
		/* Some debug dumps of the intermediate CAS. */
		if (logger.isDebugEnabled()) {
			builder.add(AnalysisEngineFactory.createEngineDescription(DumpConstituents.class));
			builder.add(AnalysisEngineFactory.createEngineDescription(CasDumpWriter.class,
					CasDumpWriter.PARAM_OUTPUT_FILE, "/tmp/yodaqa-qacas.txt"));
		}

		AnalysisEngineDescription aed = builder.createAggregateDescription();
		aed.getAnalysisEngineMetaData().setName(
				"cz.brmlab.yodaqa.analysis.question.QuestionAnalysisAE");
		return aed;
	}
}
