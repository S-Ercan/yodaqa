package cz.brmlab.yodaqa.analysis.question;

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

public class AlpinoParser extends JCasAnnotator_ImplBase {

	private final String hostName = "localhost";
	private final int parsePortNumber = 42424;

	@Override
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
	}

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		FSIterator<Annotation> typeToParseIterator = aJCas.getAnnotationIndex(JCasUtil.
				getType(aJCas, Sentence.class)).iterator();

		while (typeToParseIterator.hasNext()) {
			List<Token> tokenList = new ArrayList<>();
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
			Node treeNode = parseTree.getDocumentElement();
			annotateConstituents(aJCas, tokenList, treeNode);
			annotateDependencies(aJCas, tokenList, parseOutput);
		}
	}

	private void annotateConstituents(JCas aJCas, List<Token> tokenList, Node treeNode) {
		new AlpinoConstituentAnnotator(tokenList).createConstituentAnnotationFromTree(aJCas,
				treeNode, null, true);
	}

	private void annotateDependencies(JCas aJCas, List<Token> tokenList, String parseOutput) {
		AlpinoDependencyAnnotator depAnnotator = AlpinoDependencyAnnotator.getInstance();
		String dependencyOutput = depAnnotator.getDependencyTriplesFromParseTree(parseOutput);
		depAnnotator.processDependencyTriples(aJCas, tokenList, dependencyOutput);
	}

	private String getParseOutput(String sentence) {
		Socket parseSocket = null;
		PrintWriter parseOut = null;
		BufferedReader parseIn = null;
		try {
			parseSocket = new Socket(hostName, parsePortNumber);
			parseOut = new PrintWriter(parseSocket.getOutputStream(), true);
			parseIn = new BufferedReader(new InputStreamReader(parseSocket.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (parseSocket == null || parseOut == null || parseIn == null) {
			System.out.println("Couldn't initialize communication objects.");
			return "";
		}
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
		if (parseSocket != null) {
			try {
				parseSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
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
