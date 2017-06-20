package cz.brmlab.yodaqa.io.remote;

import cz.brmlab.yodaqa.YodaQA_Remote;
import cz.brmlab.yodaqa.analysis.passage.MatchQuestionClues;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.fit.component.JCasConsumer_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import cz.brmlab.yodaqa.flow.dashboard.Question;
import cz.brmlab.yodaqa.flow.dashboard.QuestionDashboard;
import cz.brmlab.yodaqa.model.AnswerHitlist.Answer;
import cz.brmlab.yodaqa.model.Question.QuestionInfo;

/**
 *
 */
public class AnswerPrinter extends JCasConsumer_ImplBase {

	@Override
	public void initialize(UimaContext context)
			throws ResourceInitializationException {
		super.initialize(context);
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		JCas questionView, answerHitlist;
		try {
			questionView = jcas.getView("Question");
			answerHitlist = jcas.getView("AnswerHitlist");
		} catch (Exception e) {
			throw new AnalysisEngineProcessException(e);
		}
		QuestionInfo qi = JCasUtil.selectSingle(questionView, QuestionInfo.class);
		FSIndex idx = answerHitlist.getJFSIndexRepository().getIndex("SortedAnswers");
		FSIterator answers = idx.iterator();
		printResponse(answers);

		Question q = QuestionDashboard.getInstance().get(qi.getQuestionId());
		QuestionDashboard.getInstance().finishQuestion(q);
	}

	private void printResponse(FSIterator answers) {
		String response;
		if (answers.hasNext()) {
			if (QuestionDashboard.getInstance().isConfirmationQuestion()) {
				Answer answer = (Answer) answers.next();
				if (answer.getConfidence() > 2) {
					response = "Ja";
				} else {
					response = "Nee";
				}
			} else {
				Answer answer = (Answer) answers.next();
				if (answer.getConfidence() > 0.5) {
					response = answer.getText().replace('\n', ' ');
					response = response.substring(0, Math.min(response.length(), 200));
				} else {
					response = "NO_ANSWER";
				}
			}
		} else {
			response = "No answer found.";
		}
		YodaQA_Remote.out.println(response);
	}
}
