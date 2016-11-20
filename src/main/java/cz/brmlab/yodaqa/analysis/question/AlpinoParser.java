package cz.brmlab.yodaqa.analysis.question;

import cz.brmlab.yodaqa.model.PickedPassage.PickedPassageInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.Socket;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import java.util.Iterator;

public class AlpinoParser extends JCasAnnotator_ImplBase {

	private String hostName = "localhost";
	private int parsePortNumber = 42424;

	private Socket parseSocket;
	private PrintWriter parseOut;
	private BufferedReader parseIn;

	private AlpinoConstituentAnnotator constituentAnnotator;
	private AlpinoDependencyAnnotator dependencyAnnotator;

	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
	}

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {

		int numPassages = getNumberOfPassagesToAnalyze(aJCas);

		FSIterator<Annotation> typeToParseIterator = aJCas.getAnnotationIndex(JCasUtil.
				getType(aJCas, Sentence.class))
				.iterator();

		while (typeToParseIterator.hasNext()) {
			try {
				// TODO: initializing this once (which should be done) leads to
				// problems in case typeToParseIterator does have multiple
				// entries; figure out a fix so we don't have to reinitialize
				// this on every iteration
				parseSocket = new Socket(hostName, parsePortNumber);
				parseOut = new PrintWriter(parseSocket.getOutputStream(), true);
				parseIn = new BufferedReader(new InputStreamReader(parseSocket.getInputStream()));
			} catch (IOException e) {
				e.printStackTrace();
			}

			ArrayList<Token> tokenList = new ArrayList<>();
			Annotation currAnnotationToParse = typeToParseIterator.next();
			// Combine tokens into sentence
			String sentence = "";
			String text;
			for (Token token : JCasUtil.selectCovered(Token.class, currAnnotationToParse)) {
				tokenList.add(token);
				text = token.getCoveredText();
				text = Normalizer.normalize(text, Normalizer.Form.NFD);
				sentence += text + ' ';
			}
			if (sentence.equals("")) {
				continue;
			}
			// Get parse tree and dependency triples
			String parseOutput = getParseOutput(sentence);
			Document parseTree = processParseTree(parseOutput);
			if (parseTree == null) {
				System.out.println(
						"No parse tree for the following sentence was received: " + sentence);
				continue;
			}
			annotateConstituents(aJCas, numPassages, tokenList, parseTree.getDocumentElement());
			annotateDependencies(numPassages, aJCas, sentence, tokenList);
			try {
				JCas ppView = aJCas.getView("PickedPassages");
			} catch (CASRuntimeException e) {
//				List<Dependency> dependencies = annotateDependencies(aJCas, sentence, tokenList);
			} catch (CASException e) {
				e.printStackTrace();
			}
		}
		if (parseSocket != null) {
			try {
				parseSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private int getNumberOfPassagesToAnalyze(JCas aJCas) {
		Iterator<PickedPassageInfo> ppInfoIterator
				= JCasUtil.select(aJCas, PickedPassageInfo.class).
						iterator();
		int numPassages = 0;
		while (ppInfoIterator.hasNext()) {
			numPassages += ppInfoIterator.next().getNumPassages();
		}
		return numPassages;
	}

	private void annotateConstituents(JCas jCas, int numPassages, List<Token> tokenList, Node treeNode) {
		constituentAnnotator = AlpinoConstituentAnnotator.getAlpinoConstituentAnnotator(
				numPassages);
		constituentAnnotator.process(jCas, tokenList, treeNode, null);
	}

	private void annotateDependencies(int numPassages, JCas aJCas, String sentence,
			ArrayList<Token> tokenList) {
		try {
			dependencyAnnotator = AlpinoDependencyAnnotator.getAlpinoDependencyAnnotator(tokenList);
			String dependencyOutput;
			try {
				dependencyOutput = dependencyAnnotator.getDependencyOutput(sentence);
				dependencyAnnotator.processDependencyTriples(aJCas, dependencyOutput);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (CASException e) {
			e.printStackTrace();
		}
	}

	private String getParseOutput(String sentence) {
		// TODO: investigate passing all sentences off at once during answer analysis.
		parseOut.println(sentence);
		String line;
		String output = "";
		try {
			while ((line = parseIn.readLine()) != null) {
				output += line;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return output;
	}

	private Document processParseTree(String parseOutput) {
		Document parseTree = null;
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			parseTree = dBuilder.parse(new InputSource(new StringReader(parseOutput)));
			parseTree.getDocumentElement().normalize();
			return parseTree;
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXParseException e) {
			System.err.println("Could not parse string.");
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return parseTree;
	}

}
