package cz.brmlab.yodaqa.analysis.question;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.N;
import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.Constituent;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.ROOT;
import edu.stanford.nlp.util.IntPair;

public class AlpinoParser extends JCasAnnotator_ImplBase {

	private JCas jCas = null;

	private List<Token> tokenList;

	private String hostName = "localhost";
	private int portNumber = 42424;

	private Socket parseSocket;
	private PrintWriter out;
	private BufferedReader in;

	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
		try {
			tokenList = new ArrayList<Token>();
			parseSocket = new Socket(hostName, portNumber);
			out = new PrintWriter(parseSocket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(parseSocket.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		setJCas(aJCas);

		String sentence = "";
		String text;
		for (Token token : JCasUtil.select(jCas, Token.class)) {
			text = token.getCoveredText();
			tokenList.add(token);
			sentence += text + ' ';
		}
		System.out.println(sentence);
		out.println(sentence);

		String line;
		String output = "";
		try {
			while ((line = in.readLine()) != null) {
				output += line;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			parseSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document parseTree = dBuilder.parse(new InputSource(new StringReader(output)));
			parseTree.getDocumentElement().normalize();

			annotateParseTree(parseTree.getDocumentElement());
		} catch (ParserConfigurationException e1) {
			e1.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void annotateParseTree(Node treeNode) {
		// System.out.println("Node: " + treeNode.getTextContent());
		// NodeList children = treeNode.getChildNodes();
		// for (int i = 0; i < children.getLength(); i++) {
		// annotateParseTree(children.item(i));
		// }
		createConstituentAnnotationFromTree(treeNode, null, true);
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
				annotateParseTree(children.item(i));
			}
			return null;
		}
		// String syntacticFunction = null;
		// AbstractTreebankLanguagePack tlp = (AbstractTreebankLanguagePack)
		// aTreebankLanguagePack;
		// int gfIdx = nodeLabelValue.indexOf(tlp.getGfCharacter());
		// if (gfIdx > 0) {
		// syntacticFunction = nodeLabelValue.substring(gfIdx + 1);
		// nodeLabelValue = nodeLabelValue.substring(0, gfIdx);
		// }

		// calculate span for the current subtree
		// IntPair span = tokenTree.getSpan(aNode);

		NamedNodeMap attrs = aNode.getAttributes();
		Node beginNode, endNode, catNode, relNode, posNode, wordNode;
		beginNode = endNode = catNode = relNode = posNode = wordNode = null;
		if (attrs != null) {
			beginNode = attrs.getNamedItem("begin");
			endNode = attrs.getNamedItem("end");
			catNode = attrs.getNamedItem("cat");
			relNode = attrs.getNamedItem("rel");
			posNode = attrs.getNamedItem("pos");
			wordNode = attrs.getNamedItem("word");
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
		} else if (relNode == null) {
//			throw new IllegalArgumentException("Node must have a category, POS, or rel label.");
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
		Type type = getJCas().getTypeSystem().getType(constituentType);
		return type;
	}

	private Type getPOSType(String posType) {
		posType = N.class.getName();
		return getJCas().getTypeSystem().getType(posType);
	}

	public JCas getJCas() {
		return jCas;
	}

	public void setJCas(JCas jCas) {
		this.jCas = jCas;
	}

}
