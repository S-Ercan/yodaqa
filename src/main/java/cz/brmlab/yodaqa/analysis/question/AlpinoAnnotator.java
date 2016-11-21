package cz.brmlab.yodaqa.analysis.question;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.NullArgumentException;
import org.apache.uima.jcas.JCas;

public abstract class AlpinoAnnotator {

	protected final String alpinoModelsPackage = "cz.brmlab.yodaqa.model.alpino.type";
	protected final String constituentPackage = alpinoModelsPackage + ".constituent";
	protected final String dependencyPackage = alpinoModelsPackage + ".dependency";
	protected final String posPackage = alpinoModelsPackage + ".pos";

	private int numPassages;
	private Map<JCas, List<Token>> tokenListByJCas = new TreeMap<>((JCas t, JCas t1) -> {
		return Integer.valueOf(t.hashCode()).compareTo(t1.hashCode());
	});

	public void process(JCas jCas, List<Token> tokenList) {
		tokenListByJCas.put(jCas, tokenList);
		if (tokenListByJCas.size() == getNumPassages()) {
			String sentences = "";
			for (List<Token> aTokenList : tokenListByJCas.values()) {
				// Combine tokens into sentence
				String text;
				for (Token token : aTokenList) {
					text = token.getCoveredText();
					text = Normalizer.normalize(text, Normalizer.Form.NFD);
					sentences += text + ' ';
				}
			}
			if (sentences.equals("")) {
				System.out.println("No sentences to process.");
			}
			// Get parse tree and dependency triples
			String parseOutput = null;
			try {
				parseOutput = getAlpinoOutput(sentences);
			} catch (IOException ex) {
				Logger.getLogger(AlpinoAnnotator.class.getName()).log(Level.SEVERE, null, ex);
			}
			processAlpinoOutput(parseOutput);
		}
	}

	protected abstract ProcessBuilder createAlpinoProcess();

	public String getAlpinoOutput(String sentence) throws IOException {
		String alpinoHome = System.getenv("ALPINO_HOME");
		if (alpinoHome == null) {
			throw new NullArgumentException("No \"ALPINO_HOME\" environment variable is specified.");
		}
		ProcessBuilder builder = createAlpinoProcess();
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
				new InputStreamReader(process.getErrorStream());
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

	protected abstract void processAlpinoOutput(String output);

	class MyRunnable implements Runnable {

		private final Process process;
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

	protected int getNumPassages() {
		return numPassages;
	}

	protected void setNumPassages(int numPassages) {
		this.numPassages = numPassages;
	}

	protected Map<JCas, List<Token>> getTokenListByJCas() {
		return tokenListByJCas;
	}

	protected void setTokenListByJCas(Map<JCas, List<Token>> tokenListByJCas) {
		this.tokenListByJCas = tokenListByJCas;
	}
}
