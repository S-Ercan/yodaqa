package cz.brmlab.yodaqa.io.remote;

import java.io.IOException;

import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;

import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.CasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;

import cz.brmlab.yodaqa.flow.dashboard.Question;
import cz.brmlab.yodaqa.flow.dashboard.QuestionDashboard;
import cz.brmlab.yodaqa.model.Question.QuestionInfo;
import cz.brmlab.yodaqa.YodaQA_Remote;
import cz.brmlab.yodaqa.pipeline.AnswerCASMerger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class QuestionReader extends CasCollectionReader_ImplBase {

	final Logger logger = LoggerFactory.getLogger(QuestionReader.class);

	/**
	 * Name of optional configuration parameter that contains the language of questions. This is
	 * mandatory as x-unspecified will break e.g. OpenNLP.
	 */
	public static final String PARAM_LANGUAGE = ComponentParameters.PARAM_LANGUAGE;
	@ConfigurationParameter(name = PARAM_LANGUAGE, mandatory = true)
	private String language;

	private int index;
	private String input;

	@Override
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
		index = -1;
	}

	protected void acquireInput() {
		index++;

		try {
			String inputLine;
			while ((inputLine = YodaQA_Remote.in.readLine()) != null) {
				input = inputLine;
				// Write question to logfile
				break;
			}
		} catch (IOException io) {
			input = null;
		}
	}

	@Override
	public boolean hasNext() throws CollectionException {
		if (input == null) {
			acquireInput();
		}
		return input != null;
	}

	protected void initCas(JCas jcas) {
		jcas.setDocumentLanguage(language);

		QuestionInfo qInfo = new QuestionInfo(jcas);
		qInfo.setSource("interactive");
		qInfo.setQuestionId(Integer.toString(index));
		qInfo.addToIndexes(jcas);
	}

	@Override
	public void getNext(CAS aCAS) throws CollectionException {
		if (input == null) {
			acquireInput();
		}

		Question q = new Question(Integer.toString(index), input);
		QuestionDashboard.getInstance().askQuestion(q);
		QuestionDashboard.getInstance().getQuestionToAnswer();

		try {
			JCas jcas = aCAS.getJCas();
			initCas(jcas);
			jcas.setDocumentText(input);
			QuestionDashboard.getInstance().setStartingTime(System.nanoTime());
		} catch (CASException e) {
			throw new CollectionException(e);
		}
		input = null;
	}

	@Override
	public Progress[] getProgress() {
		return new Progress[]{new ProgressImpl(index, -1, Progress.ENTITIES)};
	}

	@Override
	public void close() {
	}
}
