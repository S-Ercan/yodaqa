package cz.brmlab.yodaqa.analysis.question;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import cz.brmlab.yodaqa.model.alpino.type.constituent.NP;
import cz.brmlab.yodaqa.model.alpino.type.constituent.SV1;
import cz.brmlab.yodaqa.model.alpino.type.constituent.WHQ;
import cz.brmlab.yodaqa.model.alpino.type.pos.ADJ;
import cz.brmlab.yodaqa.model.alpino.type.pos.DET;
import cz.brmlab.yodaqa.model.alpino.type.pos.NAME;
import cz.brmlab.yodaqa.model.alpino.type.pos.PUNCT;
import cz.brmlab.yodaqa.model.alpino.type.pos.VERB;
import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.Constituent;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.ROOT;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;
import edu.stanford.nlp.util.IntPair;

public class AlpinoParser extends JCasAnnotator_ImplBase {

	private JCas jCas = null;

	private List<Token> tokenList;

	private String hostName = "localhost";
	private int parsePortNumber = 42424;

	private Socket parseSocket;
	private PrintWriter parseOut;
	private BufferedReader parseIn;

	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
		tokenList = new ArrayList<Token>();
	}

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		try {
			parseSocket = new Socket(hostName, parsePortNumber);
			parseOut = new PrintWriter(parseSocket.getOutputStream(), true);
			parseIn = new BufferedReader(new InputStreamReader(parseSocket.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
		}

		setJCas(aJCas);
		// Combine tokens into sentence
		String sentence = "";
		String text;
		for (Token token : JCasUtil.select(jCas, Token.class)) {
			text = token.getCoveredText();
			tokenList.add(token);
			sentence += text + ' ';
		}
		// Get parse tree and dependency triples
		String parseOutput = getParseOutput(sentence);
		processParseTree(parseOutput);
		String dependencyOutput;
		try {
			dependencyOutput = getDependencyOutput(sentence);
			processDependencyTriples(dependencyOutput);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String getParseOutput(String sentence) {
		parseOut.println(sentence);
		String line;
		String output = "";
		try {
			while ((line = parseIn.readLine()) != null) {
				output += line;
			}
			parseSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return output;
	}

	private void processParseTree(String parseOutput) {
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document parseTree = dBuilder.parse(new InputSource(new StringReader(parseOutput)));
			parseTree.getDocumentElement().normalize();
			createConstituentAnnotationFromTree(parseTree.getDocumentElement(), null, true);
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Annotation createConstituentAnnotationFromTree(Node aNode, Annotation aParentFS, boolean aCreatePos) {
		if (aNode.getNodeName().equals("node")) {
			NamedNodeMap attrs = aNode.getAttributes();
			if (attrs != null) {
				System.out.println("\n" + attrs.getNamedItem("begin") + ", " + attrs.getNamedItem("end") + ", "
						+ attrs.getNamedItem("rel"));
				Node word = attrs.getNamedItem("word");
				Node pos = attrs.getNamedItem("pos");
				Node lemma = attrs.getNamedItem("lemma");
				if (word != null) {
					System.out.println(word + ", " + pos + ", " + lemma);
				}
			}
		} else {
			NodeList children = aNode.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				createConstituentAnnotationFromTree(children.item(i), null, true);
			}
			return null;
		}

		NamedNodeMap attrs = aNode.getAttributes();
		Node beginNode, endNode, catNode, posNode;
		beginNode = endNode = catNode = posNode = null;
		if (attrs != null) {
			beginNode = attrs.getNamedItem("begin");
			endNode = attrs.getNamedItem("end");
			catNode = attrs.getNamedItem("cat");
			posNode = attrs.getNamedItem("pos");
		}

		int firstTokenIndex = Integer.parseInt(beginNode.getNodeValue());
		int lastTokenIndex = Integer.parseInt(endNode.getNodeValue()) - 1;
		Token token = tokenList.get(firstTokenIndex);
		int nodeBeginIndex = tokenList.get(firstTokenIndex).getBegin();
		int nodeEndIndex = tokenList.get(lastTokenIndex).getEnd();
		IntPair span = new IntPair(nodeBeginIndex, nodeEndIndex);

		if (catNode != null) {
			// add annotation to annotation tree
			Constituent constituent = createConstituentAnnotation(span.getSource(), span.getTarget(),
					catNode.getNodeValue(), null);
			// link to parent
			if (aParentFS != null) {
				constituent.setParent(aParentFS);
			}

			// Do we have any children?
			List<Annotation> childAnnotations = new ArrayList<Annotation>();
			NodeList childNodes = aNode.getChildNodes();
			for (int i = 0; i < childNodes.getLength(); i++) {
				Annotation childAnnotation = createConstituentAnnotationFromTree(childNodes.item(i), constituent,
						aCreatePos);
				if (childAnnotation != null) {
					childAnnotations.add(childAnnotation);
				}
			}

			// Now that we know how many children we have, link annotation of
			// current node with its children
			FSArray children = new FSArray(jCas, childAnnotations.size());
			int curChildNum = 0;
			for (FeatureStructure child : childAnnotations) {
				children.set(curChildNum, child);
				curChildNum++;
			}
			constituent.setChildren(children);

			// write annotation for current node to index
			jCas.addFsToIndexes(constituent);

			return constituent;
		} else if (posNode != null) {
			// create POS-annotation (annotation over the token)
			POS pos = createPOSAnnotation(span.getSource(), span.getTarget(), posNode.getNodeValue());

			// only add POS to index if we want POS-tagging
			if (aCreatePos) {
				jCas.addFsToIndexes(pos);
				token.setPos(pos);
			}

			// link token to its parent constituent
			if (aParentFS != null) {
				token.setParent(aParentFS);
			}

			return token;
		}
		return null;
	}

	public Constituent createConstituentAnnotation(int aBegin, int aEnd, String aConstituentType,
			String aSyntacticFunction) {
		// Type constType =
		// constituentMappingProvider.getTagType(aConstituentType);
		Type constType = getConstituentType(aConstituentType);

		Constituent constAnno = (Constituent) jCas.getCas().createAnnotation(constType, aBegin, aEnd);
		constAnno.setConstituentType(aConstituentType);
		constAnno.setSyntacticFunction(aSyntacticFunction);
		return constAnno;
	}

	public POS createPOSAnnotation(int aBegin, int aEnd, String aPosType) {
		// Type type = posMappingProvider.getTagType(aPosType);
		Type type = getPOSType(aPosType);

		// create instance of the desired type
		POS anno = (POS) jCas.getCas().createAnnotation(type, aBegin, aEnd);

		// save original (unmapped) postype in feature
		anno.setPosValue(aPosType);

		return anno;
	}

	private Type getConstituentType(String constituentType) {
		switch (constituentType) {
		case "top":
			constituentType = ROOT.class.getName();
			break;
		case "whq":
			constituentType = WHQ.class.getName();
			break;
		case "sv1":
			constituentType = SV1.class.getName();
			break;
		case "np":
			constituentType = NP.class.getName();
			break;
		default:
			constituentType = Constituent.class.getName();
		}
		return getJCas().getTypeSystem().getType(constituentType);
	}

	private Type getPOSType(String posType) {
		switch (posType) {
		case "adj":
			posType = ADJ.class.getName();
			break;
		case "verb":
			posType = VERB.class.getName();
			break;
		case "det":
			posType = DET.class.getName();
			break;
		case "name":
			posType = NAME.class.getName();
			break;
		case "punct":
			posType = PUNCT.class.getName();
			break;
		default:
			posType = POS.class.getName();
		}
		return getJCas().getTypeSystem().getType(posType);
	}

	private String getDependencyOutput(String sentence) throws IOException {
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "bin/Alpino", "end_hook=triples", "-parse", "-notk");
		builder.directory(new File("/home/selman/Software/Libraries/Alpino/"));
		Map<String, String> env = builder.environment();
		env.put("ALPINO_HOME", "/home/selman/Software/Libraries/Alpino/");

		final Process process = builder.start();

		Thread inThread = new Thread() {
			@Override
			public void run() {
				PrintWriter out = new PrintWriter(process.getOutputStream());
				out.println("Wat is een robot?");
				out.flush();
				out.close();
			}
		};
		inThread.start();

		MyRunnable myRunnable = new MyRunnable(process);
		Thread outThread = new Thread(myRunnable);
		outThread.start();

		Thread err = new Thread() {
			@Override
			public void run() {
				InputStreamReader is = new InputStreamReader(process.getErrorStream());
				Scanner scanner = new Scanner(is);
				String line = null;
				while (scanner.hasNextLine()) {
					line = scanner.nextLine();
					System.out.println(line);
				}
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				scanner.close();
			}
		};
		err.start();

		try {
			process.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return myRunnable.getOutput();
	}

	class MyRunnable implements Runnable {
		private Process process;
		private String output;

		public MyRunnable(Process process) {
			this.process = process;
		}

		public String getOutput() {
			return output;
		}

		public void setOutput(String output) {
			this.output = output;
		}

		@Override
		public void run() {
			// For reading process output
			InputStreamReader is = new InputStreamReader(process.getInputStream());
			Scanner scanner = new Scanner(is);
			// For writing process output to string
			StringWriter strWriter = new StringWriter();
			PrintWriter writer = new PrintWriter(strWriter, true);
			// Read process output
			while (scanner.hasNextLine()) {
				writer.println(scanner.nextLine());
			}
			setOutput(strWriter.toString());
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			scanner.close();
		}
	}

	private void processDependencyTriples(String output) {
		for (String triple : output.split("\n")) {
			Pattern pattern = Pattern.compile("(.+)[\\|]{1}(.+)[\\|]{1}(.+)[\\|]{1}");
			Matcher matcher = pattern.matcher(triple);
			if (matcher.find()) {
				String governorString = matcher.group(1);
				if (governorString.equals("top/top")) {
					continue;
				}
				String dependencyString = matcher.group(2).split("/")[1];
				String dependentString = matcher.group(3);

				String aDependencyType = dependencyString;
				String dependencyTypeName = "cz.brmlab.yodaqa.model.alpino.type.dependency."
						+ aDependencyType.toUpperCase();

				Type type = jCas.getTypeSystem().getType(dependencyTypeName);
				if (type == null) {
					type = JCasUtil.getType(jCas, Dependency.class);
				}

				Pattern digitPattern = Pattern.compile("\\d+");
				Matcher digitMatcher = digitPattern.matcher(governorString);
				digitMatcher.find();
				int governorIndex = Integer.valueOf(digitMatcher.group());
				digitMatcher = digitPattern.matcher(dependentString);
				digitMatcher.find();
				int dependentIndex = Integer.valueOf(digitMatcher.group());

				Dependency dep = (Dependency) jCas.getCas().createFS(type);
				dep.setDependencyType(aDependencyType.toString());
				dep.setGovernor(tokenList.get(governorIndex));
				dep.setDependent(tokenList.get(dependentIndex));
				dep.setBegin(dep.getDependent().getBegin());
				dep.setEnd(dep.getDependent().getEnd());
				dep.addToIndexes();
			}
		}
	}

	public JCas getJCas() {
		return jCas;
	}

	public void setJCas(JCas jCas) {
		this.jCas = jCas;
	}

}
