package cz.brmlab.yodaqa.analysis.question;

import java.util.ArrayList;
import java.util.List;

import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.Constituent;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.ROOT;
import edu.stanford.nlp.util.IntPair;

public class AlpinoAnnotator {

	private String alpinoModelsPackage = "cz.brmlab.yodaqa.model.alpino.type";
	private String constituentPackage = alpinoModelsPackage + ".constituent";
	private String dependencyPackage = alpinoModelsPackage + ".dependency";
	private String posPackage = alpinoModelsPackage + ".pos";

	private JCas jCas = null;

	private List<Token> tokenList;

	public AlpinoAnnotator(List<Token> tokenList) throws CASException {
		setTokenList(tokenList);
		setJCas(tokenList.get(0).getCAS().getJCas());
	}

	public JCas getJCas() {
		return jCas;
	}

	public void setJCas(JCas aJCas) {
		jCas = aJCas;
	}

	public List<Token> getTokenList() {
		return tokenList;
	}

	public void setTokenList(List<Token> tokenList) {
		this.tokenList = tokenList;
	}

	public Annotation createConstituentAnnotationFromTree(Node aNode, Annotation aParentFS, boolean aCreatePos) {
		if (!aNode.getNodeName().equals("node")) {
			// NamedNodeMap attrs = aNode.getAttributes();
			// if (attrs != null) {
			// System.out.println("\n" + attrs.getNamedItem("begin") + ", " +
			// attrs.getNamedItem("end") + ", "
			// + attrs.getNamedItem("rel"));
			// Node word = attrs.getNamedItem("word");
			// Node pos = attrs.getNamedItem("pos");
			// Node lemma = attrs.getNamedItem("lemma");
			// if (word != null) {
			// System.out.println(word + ", " + pos + ", " + lemma);
			// }
			// }
			// } else {
			// NodeList children = aNode.getChildNodes();
			// for (int i = 0; i < children.getLength(); i++) {
			// createConstituentAnnotationFromTree(children.item(i), null,
			// true);
			// }
			// return null;
			if (aNode.getChildNodes().getLength() > 0) {
				return createConstituentAnnotationFromTree(aNode.getChildNodes().item(1), null, true);
			} else {
				return null;
			}
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
		Token token = getTokenList().get(firstTokenIndex);
		int nodeBeginIndex = token.getBegin();
		int nodeEndIndex = getTokenList().get(lastTokenIndex).getEnd();

		if (nodeBeginIndex == 0 && nodeEndIndex == 0) {
			System.out.println();
		}

		// TODO: sometimes getting nodeBeginIndex == 0 && nodeEndIndex == 0
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
					// System.out.println("childAnnotation: " +
					// childAnnotation.getCoveredText());
					// TODO: this happens and it shouldn't
					if (!jCas.getCas().equals(childAnnotation.getCAS())) {
						System.out.println();
					}
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
				if (!token.getCAS().equals(pos.getCAS())) {
					System.out.println("Incorrect: " + jCas.getCas() + ", " + token.getCAS() + ", " + pos.getCAS());
				} else {
					System.out.println("Correct: " + jCas.getCas() + ", " + token.getCAS() + ", " + pos.getCAS());
				}
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
		Type constType;
		if (aConstituentType.equals("top")) {
			constType = jCas.getTypeSystem().getType(ROOT.class.getName());
		} else {
			constType = jCas.getTypeSystem().getType(constituentPackage + "." + aConstituentType.toUpperCase());
		}
		Constituent constAnno = (Constituent) jCas.getCas().createAnnotation(constType, aBegin, aEnd);
		constAnno.setConstituentType(aConstituentType);
		constAnno.setSyntacticFunction(aSyntacticFunction);

		return constAnno;
	}

	public POS createPOSAnnotation(int aBegin, int aEnd, String aPosType) {
		Type type = jCas.getTypeSystem().getType(posPackage + "." + aPosType.toUpperCase());
		// create instance of the desired type
		POS anno = (POS) jCas.getCas().createAnnotation(type, aBegin, aEnd);
		// save original (unmapped) postype in feature
		anno.setPosValue(aPosType);

		return anno;
	}

}
