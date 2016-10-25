package cz.brmlab.yodaqa.analysis.question;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.Socket;
import java.text.Normalizer;
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
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Type;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;
import edu.stanford.nlp.ling.HasWord;

public class AlpinoParser extends JCasAnnotator_ImplBase {

	private List<Token> tokenList;

	private String hostName = "localhost";
	private int parsePortNumber = 42424;

	private Socket parseSocket;
	private PrintWriter parseOut;
	private BufferedReader parseIn;

	private String alpinoModelsPackage = "cz.brmlab.yodaqa.model.alpino.type";
	private String constituentPackage = alpinoModelsPackage + ".constituent";
	private String dependencyPackage = alpinoModelsPackage + ".dependency";
	private String posPackage = alpinoModelsPackage + ".pos";

	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
		setTokenList(new ArrayList<Token>());
	}

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		JCas questionView, passagesView;
		questionView = passagesView = null;
		try {
			questionView = aJCas.getView("Question");
			passagesView = aJCas.getView("PickedPassages");
		} catch (CASException e) {
			System.out.println("Not yet...");
		} catch (CASRuntimeException e) {
			System.out.println("Not yet...");
		}

		System.out.println(questionView);
		System.out.println(passagesView);

		try {
			parseSocket = new Socket(hostName, parsePortNumber);
			parseOut = new PrintWriter(parseSocket.getOutputStream(), true);
			parseIn = new BufferedReader(new InputStreamReader(parseSocket.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
		}

		// setJCas(aJCas);
		FSIterator<Annotation> typeToParseIterator = aJCas.getAnnotationIndex(JCasUtil.getType(aJCas, Sentence.class))
				.iterator();

		while (typeToParseIterator.hasNext()) {
			Annotation currAnnotationToParse = typeToParseIterator.next();
			// Split sentence to tokens for annotating indexes
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
				System.out.println("No parse tree for the following sentence was received: " + sentence);
				continue;
			}
			AlpinoAnnotator annotator;
			try {
				annotator = new AlpinoAnnotator(getTokenList());
				annotator.createConstituentAnnotationFromTree(parseTree.getDocumentElement(), null, true);
			} catch (CASException e) {
				e.printStackTrace();
			}
			// String dependencyOutput;
			// try {
			// dependencyOutput = getDependencyOutput(sentence);
			// processDependencyTriples(dependencyOutput);
			// } catch (IOException e) {
			// e.printStackTrace();
			// }
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
				out.println(sentence);
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

	private void processDependencyTriples(JCas jCas, String output) {
		if (output == null || output.equals("")) {
			System.out.println("No dependency triples received");
			return;
		}
		for (String triple : output.split("\n")) {
			Pattern pattern = Pattern.compile("(.+)[\\|](.+)[\\|](.+)[\\|]");
			Matcher matcher = pattern.matcher(triple);
			if (matcher.find()) {
				String governorString = matcher.group(1);
				if (governorString.equals("top/top")) {
					continue;
				}
				String dependencyString = matcher.group(2).split("/")[1];
				String dependentString = matcher.group(3);

				String aDependencyType = dependencyString;
				String dependencyTypeName = dependencyPackage + "." + aDependencyType.toUpperCase();
				System.out.println(dependencyTypeName);

				Type type = jCas.getTypeSystem().getType(dependencyTypeName);
				if (type == null) {
					type = JCasUtil.getType(jCas, Dependency.class);
				}

				Pattern digitPattern = Pattern.compile(".+[\\[](\\d+)[,]");
				Matcher digitMatcher = digitPattern.matcher(governorString);
				digitMatcher.find();
				int governorIndex = Integer.valueOf(digitMatcher.group(1));
				digitMatcher = digitPattern.matcher(dependentString);
				digitMatcher.find();
				int dependentIndex = Integer.valueOf(digitMatcher.group(1));

				// if (governorIndex > tokenList.size() || dependentIndex >
				// tokenList.size()) {
				// System.out.println(governorIndex + ", " + dependentIndex);
				// } else if (tokenList.get(governorIndex) == null ||
				// tokenList.get(dependentIndex) == null) {
				// System.out.println(governorIndex + ", " + dependentIndex);
				// }

				Token governor = getTokenList().get(governorIndex);
				Token dependent = getTokenList().get(dependentIndex);

				System.out.println(governor.getBegin());
				System.out.println(governor.getEnd());
				System.out.println(dependent.getBegin());
				System.out.println(dependent.getEnd());

				Dependency dep = (Dependency) jCas.getCas().createFS(type);
				dep.setDependencyType(aDependencyType.toString());
				dep.setGovernor(governor);
				dep.setDependent(dependent);
				dep.setBegin(dependent.getBegin());
				dep.setEnd(dependent.getEnd());
				dep.addToIndexes();
			}
		}
	}

	public List<Token> getTokenList() {
		return tokenList;
	}

	public void setTokenList(List<Token> tokenList) {
		this.tokenList = tokenList;
	}

}
