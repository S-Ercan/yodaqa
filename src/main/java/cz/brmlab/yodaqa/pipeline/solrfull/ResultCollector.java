package cz.brmlab.yodaqa.pipeline.solrfull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.AbstractCas;
import org.apache.uima.fit.component.JCasMultiplier_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.brmlab.yodaqa.flow.dashboard.AnswerDashboard;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import java.util.logging.Level;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import static org.apache.uima.fit.util.JCasUtil.select;
import static org.apache.uima.fit.util.JCasUtil.selectCovered;
import static org.apache.uima.fit.util.JCasUtil.toText;
import org.apache.uima.util.CasCopier;

public class ResultCollector extends JCasMultiplier_ImplBase {

	final Logger logger = LoggerFactory.getLogger(ResultCollector.class);

	/**
	 * Number of CASes marked as isLast required to encounter before the final merging is performed.
	 * When multiple independent CAS multipliers are generating CASes, they each eventually produce
	 * one with an isLast marker.
	 */
	public static final String PARAM_ISLAST_BARRIER = "islast-barrier";
	@ConfigurationParameter(name = PARAM_ISLAST_BARRIER, mandatory = false, defaultValue = "1")
	protected int isLastBarrier;

	/**
	 * Reuse the first CAS received as the AnswerHitlistCAS instead of building one from scratch.
	 * This parameter is also overloaded to mean that CandidateAnswerCAS will override same-text
	 * answers in the hitlist, instead of merging with them.
	 */
	public static final String PARAM_HITLIST_REUSE = "hitlist-reuse";
	@ConfigurationParameter(name = PARAM_HITLIST_REUSE, mandatory = false, defaultValue = "false")
	protected boolean doReuseHitlist;

	/**
	 * The phase number. If non-zero, confidence of the answer is pre-set to AF.Phase(n-1)Score.
	 */
	public static final String PARAM_PHASE = "phase";
	@ConfigurationParameter(name = PARAM_PHASE, mandatory = false, defaultValue = "0")
	protected int phaseNum;

	/* #of total CASes seen, and CASes we need to see.  This is because
	 * with asynchronous CAS flow, the last generated CAS (marked with
	 * isLast) is not the last received CAS. */
	int seenCases, needCases, outputtedJCases;
	Map<String, Integer> seenALasts, needALasts;
	List<JCas> outputJCases;
	JCas outputJCas;

	protected void reset() {
		seenCases = 0;
		outputtedJCases = 0;
		needCases = 0;
		outputJCases = new ArrayList<>();
		outputJCas = null;

		seenALasts = new HashMap<>();
		needALasts = new HashMap<>();
	}

	@Override
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
		reset();
	}

	@Override
	public synchronized void process(JCas canCas) throws AnalysisEngineProcessException {
		if (needCases == 0) {
			needCases = AnswerDashboard.getAnswerDashBoard().getNumSearchResults();
		}

		if (true || outputJCas == null) {
			outputJCas = getEmptyJCas();
		}
		CAS outputCas = outputJCas.getCas();
		CasCopier.copyCas(canCas.getCas(), outputCas, true);

		try {
			outputJCas = outputCas.getJCas();
		} catch (CASException ex) {
			java.util.logging.Logger.getLogger(ResultCollector.class.getName()).
					log(Level.SEVERE, null, ex);
		}
		outputJCases.add(outputJCas);
		seenCases++;
		if (seenCases == needCases) {
			AnswerDashboard.getAnswerDashBoard().getAlpinoOutput();
		}
	}

	protected boolean checkHasNext() {
		return seenCases >= needCases && outputtedJCases < seenCases;
	}

	boolean gotHasNext = false;

	@Override
	public synchronized boolean hasNext() throws AnalysisEngineProcessException {
		boolean ret = checkHasNext();
		if (!ret) {
			return false;
		}
		/* We have problems with race conditions as per below,
		 * this is another line of detection. */
		if (!gotHasNext) {
			gotHasNext = true;
		} else {
			logger.warn("Warning, hasNext()=true twice before a next() invocation");
			new Exception().printStackTrace(System.out);
		}
		return ret;
	}

	@Override
	public synchronized AbstractCas next() throws AnalysisEngineProcessException {
		gotHasNext = false;
		if (!checkHasNext()) {
			/* XXX: Ideally, this shouldn't happen.  However,
			 * the CAS merger interface is racy in the multi-
			 * threaded scenario: two threads simultanously
			 * call process() to feed their last CASes, only
			 * after both process() are processed they both
			 * call hasNext(), and it returns true both times,
			 * making both threads call next().  So don't make
			 * a big fuss about this. */
			logger.warn("Warning, racy CAS merger: next() on exhausted merger");
			new Exception().printStackTrace(System.out);
			return null;
		}

		JCas outputCas = outputJCases.get(outputtedJCases);
		outputtedJCases++;
		return outputCas;
	}

	@Override
	public int getCasInstancesRequired() {
//		return MultiThreadASB.maxJobs * 2;
		return 100;
	}
}
