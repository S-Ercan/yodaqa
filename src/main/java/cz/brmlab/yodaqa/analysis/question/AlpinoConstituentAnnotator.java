package cz.brmlab.yodaqa.analysis.question;

import java.util.ArrayList;
import java.util.List;

import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.Constituent;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.ROOT;
import edu.stanford.nlp.util.IntPair;
import java.util.HashMap;
import java.util.Map;

public class AlpinoConstituentAnnotator {

	private static AlpinoConstituentAnnotator constituentAnnotator = null;

	private final String alpinoModelsPackage = "cz.brmlab.yodaqa.model.alpino.type";
	private final String constituentPackage = alpinoModelsPackage + ".constituent";
	private final String posPackage = alpinoModelsPackage + ".pos";

	private int numPassages;
	private Map<JCas, List<Token>> tokenListByJCas = new HashMap<>();

	public static AlpinoConstituentAnnotator getAlpinoConstituentAnnotator(int numPassages) {
		if (constituentAnnotator == null) {
			constituentAnnotator = new AlpinoConstituentAnnotator(numPassages);
		}
		return constituentAnnotator;
	}

	private AlpinoConstituentAnnotator(int numPassages) {
		setNumPassages(numPassages);
	}

	public void process(JCas jCas, List<Token> tokenList, Node aNode, Annotation aParentFS) {
		tokenListByJCas.put(jCas, tokenList);
		if (tokenListByJCas.size() == getNumPassages()) {
			for (Map.Entry<JCas, List<Token>> entry : tokenListByJCas.entrySet()) {
				JCas aJCas = entry.getKey();
				List<Token> aTokenList = entry.getValue();
				createConstituentAnnotationFromTree(aJCas, aTokenList, aNode, aParentFS);
			}
		}
	}

	public Annotation createConstituentAnnotationFromTree(JCas jCas, List<Token> tokenList,
			Node aNode, Annotation aParentFS) {
		if (!aNode.getNodeName().equals("node")) {
			if (aNode.getChildNodes().getLength() > 0) {
				return createConstituentAnnotationFromTree(jCas, tokenList, aNode.getChildNodes().
						item(1), null);
			} else {
				return null;
			}
		}

		NamedNodeMap attrs = aNode.getAttributes();
		Node beginNode, endNode, catNode, posNode, lemmaNode;
		beginNode = endNode = catNode = posNode = lemmaNode = null;
		if (attrs != null) {
			beginNode = attrs.getNamedItem("begin");
			endNode = attrs.getNamedItem("end");
			catNode = attrs.getNamedItem("cat");
			posNode = attrs.getNamedItem("pos");
			lemmaNode = attrs.getNamedItem("lemma");
		}

		int firstTokenIndex = Integer.parseInt(beginNode.getNodeValue());
		int lastTokenIndex = Integer.parseInt(endNode.getNodeValue()) - 1;
		Token token = tokenList.get(firstTokenIndex);
		int nodeBeginIndex = token.getBegin();
		int nodeEndIndex = tokenList.get(lastTokenIndex).getEnd();

		IntPair span = new IntPair(nodeBeginIndex, nodeEndIndex);

		if (catNode != null) {
			// add annotation to annotation tree
			Constituent constituent
					= createConstituentAnnotation(jCas, span.getSource(), span.getTarget(), catNode.
							getNodeValue(), null);
			// link to parent
			if (aParentFS != null) {
				constituent.setParent(aParentFS);
			}

			// Do we have any children?
			List<Annotation> childAnnotations = new ArrayList<>();
			NodeList childNodes = aNode.getChildNodes();
			for (int i = 0; i < childNodes.getLength(); i++) {
				Annotation childAnnotation = createConstituentAnnotationFromTree(jCas, tokenList,
						childNodes.item(i), constituent);
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
			POS pos = createPOSAnnotation(jCas, span.getSource(), span.getTarget(),
					posNode.getNodeValue());

			jCas.addFsToIndexes(pos);
			token.setPos(pos);

			// link token to its parent constituent
			if (aParentFS != null) {
				token.setParent(aParentFS);
			}

			Lemma lemmaAnno = new Lemma(jCas, token.getBegin(), token.getEnd());
			lemmaAnno.setValue(lemmaNode.getNodeValue());
			jCas.addFsToIndexes(lemmaAnno);
			token.setLemma(lemmaAnno);

			return token;
		}
		return null;
	}

	public Constituent createConstituentAnnotation(JCas jCas, int aBegin, int aEnd,
			String aConstituentType, String aSyntacticFunction) {
		Type constType;
		if (aConstituentType.equals("top")) {
			constType = jCas.getTypeSystem().getType(ROOT.class.getName());
		} else {
			constType = jCas.getTypeSystem().getType(
					constituentPackage + "." + aConstituentType.toUpperCase());
		}
		Constituent constAnno = (Constituent) jCas.getCas().
				createAnnotation(constType, aBegin, aEnd);
		constAnno.setConstituentType(aConstituentType);
		constAnno.setSyntacticFunction(aSyntacticFunction);
		return constAnno;
	}

	public POS createPOSAnnotation(JCas jCas, int aBegin, int aEnd, String aPosType) {
		Type type = jCas.getTypeSystem().getType(posPackage + "." + aPosType.toUpperCase());
		// create instance of the desired type
		POS anno = (POS) jCas.getCas().createAnnotation(type, aBegin, aEnd);
		// save original (unmapped) postype in feature
		anno.setPosValue(aPosType);
		return anno;
	}

	public int getNumPassages() {
		return numPassages;
	}

	public void setNumPassages(int numPassages) {
		this.numPassages = numPassages;
	}

}
