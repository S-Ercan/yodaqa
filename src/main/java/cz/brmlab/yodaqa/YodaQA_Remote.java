package cz.brmlab.yodaqa;

import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;

import cz.brmlab.yodaqa.flow.MultiCASPipeline;
import cz.brmlab.yodaqa.flow.asb.ParallelEngineFactory;
import cz.brmlab.yodaqa.io.remote.QuestionReader;
import cz.brmlab.yodaqa.io.remote.AnswerPrinter;
import cz.brmlab.yodaqa.pipeline.YodaQA;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.factory.CollectionReaderFactory.createReaderDescription;

public class YodaQA_Remote {

	private static final int PORT = 1111;

	private static ServerSocket serverSocket;
	private static Socket clientSocket;
	public static PrintWriter out;
	public static BufferedReader in;

	public static void main(String[] args) throws Exception {

		serverSocket = new ServerSocket(PORT);
		clientSocket = serverSocket.accept();
		out = new PrintWriter(clientSocket.getOutputStream(), true);
		in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

		CollectionReaderDescription reader = createReaderDescription(
				QuestionReader.class, QuestionReader.PARAM_LANGUAGE, "en");

		AnalysisEngineDescription pipeline = YodaQA.createEngineDescription();
		AnalysisEngineDescription printer = createEngineDescription(AnswerPrinter.class);

		ParallelEngineFactory.registerFactory();
		MultiCASPipeline.runPipeline(reader, pipeline, printer);
	}
}
