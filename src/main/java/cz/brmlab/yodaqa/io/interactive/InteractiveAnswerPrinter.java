package cz.brmlab.yodaqa.io.interactive;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.component.JCasConsumer_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import cz.brmlab.yodaqa.flow.dashboard.Question;
import cz.brmlab.yodaqa.flow.dashboard.QuestionDashboard;
import cz.brmlab.yodaqa.model.AnswerHitlist.Answer;
import cz.brmlab.yodaqa.model.CandidateAnswer.AnswerResource;
import cz.brmlab.yodaqa.model.Question.QuestionInfo;

/**
 * A trivial consumer that will extract the final answer and print it on the standard output for the
 * user to "officially" see.
 *
 * Pair this with InteractiveQuestionReader.
 */
public class InteractiveAnswerPrinter extends JCasConsumer_ImplBase {

	private final boolean onlyPrintTopAnswer = false;

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
		if (answers.hasNext()) {
			int i = 1;
			if (QuestionDashboard.getInstance().getIsConfirmationQuestion()) {
				Answer answer = (Answer) answers.next();
				if (answer.getConfidence() > 2) {
					System.out.println("Ja");
				} else {
					System.out.println("Nee");
				}
			} else {
				while (answers.hasNext()) {
					Answer answer = (Answer) answers.next();
					StringBuilder sb = new StringBuilder();
					if (onlyPrintTopAnswer) {
						sb.append(answer.getText());
					} else {
						sb.append(i++);
						sb.append(". ");
						sb.append(answer.getText());
						sb.append(" (conf. ");
						sb.append(answer.getConfidence());
						sb.append(")");
						if (answer.getResources() != null) {
							for (FeatureStructure resfs : answer.getResources().toArray()) {
								sb.append(" ");
								sb.append(((AnswerResource) resfs).getIri());
							}
						}
					}
					System.out.println(sb.toString());
					if (onlyPrintTopAnswer) {
						break;
					}
				}
			}
		} else {
			System.out.println("No answer found.");
		}
	}
}
