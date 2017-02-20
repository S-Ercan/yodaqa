package cz.brmlab.yodaqa.analysis.question;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.NullArgumentException;
import org.apache.uima.cas.Type;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;

public class AlpinoDependencyAnnotator {

	private final String alpinoModelsPackage = "cz.brmlab.yodaqa.model.alpino.type";
	private final String dependencyPackage = alpinoModelsPackage + ".dependency";

	private static AlpinoDependencyAnnotator dependencyAnnotator;
	private Processor processor;
	private XQueryExecutable queryExecutable;

	private AlpinoDependencyAnnotator() {
		initializeXQueryExecutable();
	}

	public static AlpinoDependencyAnnotator getInstance() {
		if (dependencyAnnotator == null) {
			dependencyAnnotator = new AlpinoDependencyAnnotator();
		}
		return dependencyAnnotator;
	}

	private void initializeXQueryExecutable() {
		String alpinoHome = System.getenv("ALPINO_HOME");
		if (alpinoHome == null) {
			throw new NullArgumentException("No \"ALPINO_HOME\" environment variable is specified.");
		}
		File xQueryFile = new File(Paths.
				get(alpinoHome, "TreebankTools", "Xquery", "triples_ids.xq").toString());

		// Create processor and build XML document
		Processor processor = new Processor(new Configuration());
		this.processor = processor;

		// Create compiler and compile XQuery file
		XQueryCompiler compiler = processor.newXQueryCompiler();
		XQueryExecutable queryExecutable = null;
		try {
			queryExecutable = compiler.compile(xQueryFile);
		} catch (SaxonApiException | IOException ex) {
			Logger.getLogger(AlpinoDependencyAnnotator.class.getName()).log(Level.SEVERE, null, ex);
		}
		this.queryExecutable = queryExecutable;
	}

	public String getDependencyTriplesFromParseTree(String parseOutput) {
		StreamSource source = new StreamSource(new StringReader(parseOutput));
		DocumentBuilder builder = processor.newDocumentBuilder();
		XdmNode xdmNode = null;
		try {
			xdmNode = builder.build(source);
		} catch (SaxonApiException ex) {
			Logger.getLogger(AlpinoDependencyAnnotator.class.getName()).log(Level.SEVERE, null, ex);
		}

		// Evaluate query on XML document
		String triples = "";
		if (queryExecutable != null && xdmNode != null) {
			XQueryEvaluator queryEvaluator = queryExecutable.load();
			queryEvaluator.setContextItem(xdmNode);
			XdmSequenceIterator iterator = queryEvaluator.iterator();
			while (iterator.hasNext()) {
				triples += iterator.next().getStringValue();
			}
		}
		return triples;
	}

	public List<Dependency> processDependencyTriples(JCas jCas, List<Token> tokenList, String output) {
		ArrayList<Dependency> dependencies = new ArrayList<>();
		if (output == null || output.equals("")) {
			System.out.println("No dependency triples received");
			return dependencies;
		}
		for (String triple : output.split("\n")) {
			Pattern pattern = Pattern.compile("(.+)[\\|](.+)[\\|](.+)[\\|]");
			Matcher matcher = pattern.matcher(triple);
			if (matcher.find()) {
				String governorString = matcher.group(1);
				if (governorString.equals("top/top")) {
					continue;
				}

				String[] aDependencyTypes = matcher.group(2).split("/");
				String aDependencyType;
				if (aDependencyTypes[1].equals("--")) {
					continue;
				} else if (aDependencyTypes[1].equals("body")) {
					aDependencyType = aDependencyTypes[0];
				} else {
					aDependencyType = aDependencyTypes[1];
				}
				String dependentString = matcher.group(3);
				String dependencyTypeName = dependencyPackage + "." + aDependencyType.toUpperCase();

				Type type = jCas.getTypeSystem().getType(dependencyTypeName);
				if (type == null) {
					type = JCasUtil.getType(jCas, Dependency.class);
				}

				Pattern digitPattern = Pattern.compile(".+[\\[](\\d+)[\\]]");
				Matcher digitMatcher = digitPattern.matcher(governorString);
				digitMatcher.find();
				int governorIndex = Integer.valueOf(digitMatcher.group(1));
				digitMatcher = digitPattern.matcher(dependentString);
				digitMatcher.find();
				int dependentIndex = Integer.valueOf(digitMatcher.group(1));

				Token governor = tokenList.get(governorIndex);
				Token dependent = tokenList.get(dependentIndex);

				Dependency dep = (Dependency) jCas.getCas().createFS(type);
				dep.setDependencyType(aDependencyType);
				dep.setGovernor(governor);
				dep.setDependent(dependent);
				dep.setBegin(dependent.getBegin());
				dep.setEnd(dependent.getEnd());
				dep.addToIndexes();
				dependencies.add(dep);
			}
		}
		return dependencies;
	}
}
