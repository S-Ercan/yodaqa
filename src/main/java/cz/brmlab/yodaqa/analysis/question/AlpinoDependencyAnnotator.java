package cz.brmlab.yodaqa.analysis.question;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.NullArgumentException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.Type;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;

public class AlpinoDependencyAnnotator {

	private JCas jCas = null;

	private List<Token> tokenList;

	private String alpinoModelsPackage = "cz.brmlab.yodaqa.model.alpino.type";
	private String dependencyPackage = alpinoModelsPackage + ".dependency";

	public AlpinoDependencyAnnotator(List<Token> tokenList) throws CASException {
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

	public String getDependencyOutput(String sentence) throws IOException {
		String alpinoHome = System.getenv("ALPINO_HOME");
		if (alpinoHome == null) {
			throw new NullArgumentException("No \"ALPINO_HOME\" environment variable is specified.");
		}
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "bin/Alpino", "end_hook=triples", "-parse", "-notk");
		builder.directory(new File(alpinoHome));
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

	public List<Dependency> processDependencyTriples(JCas jCas, String output) {
		ArrayList<Dependency> dependencies = new ArrayList<Dependency>();
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
				String aDependencyType = matcher.group(2).split("/")[1];
				String dependentString = matcher.group(3);
				String dependencyTypeName = dependencyPackage + "." + aDependencyType.toUpperCase();

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

				Token governor = getTokenList().get(governorIndex);
				Token dependent = getTokenList().get(dependentIndex);

				Dependency dep = (Dependency) jCas.getCas().createFS(type);
				dep.setDependencyType(aDependencyType.toString());
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
