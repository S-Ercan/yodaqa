package cz.brmlab.yodaqa.analysis.question;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.annolab.tt4j.TokenHandler;
import org.annolab.tt4j.TreeTaggerException;
import org.annolab.tt4j.TreeTaggerWrapper;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.resources.CasConfigurableProviderBase;
import de.tudarmstadt.ukp.dkpro.core.api.resources.MappingProvider;
import de.tudarmstadt.ukp.dkpro.core.api.resources.MappingProviderFactory;
import de.tudarmstadt.ukp.dkpro.core.api.resources.ModelProviderBase;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.treetagger.internal.DKProExecutableResolver;
import edu.stanford.nlp.ling.Word;

import static java.util.Arrays.asList;
import static org.apache.uima.util.Level.INFO;
import java.net.URL;
import java.util.Properties;

import org.annolab.tt4j.TokenAdapter;
import org.annolab.tt4j.TreeTaggerModelUtil;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.SingletonTagset;
import de.tudarmstadt.ukp.dkpro.core.api.resources.ResourceUtils;

/**
 * Part-of-Speech and lemmatizer annotator using TreeTagger.
 */
@TypeCapability(inputs = { "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token" }, outputs = {
		"de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS",
		"de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma" })
public class TreeTagger extends JCasAnnotator_ImplBase {
	/**
	 * Use this language instead of the document language to resolve the model.
	 */
	public static final String PARAM_LANGUAGE = ComponentParameters.PARAM_LANGUAGE;
	@ConfigurationParameter(name = PARAM_LANGUAGE, mandatory = false)
	protected String language;

	/**
	 * Override the default variant used to locate the model.
	 */
	public static final String PARAM_VARIANT = ComponentParameters.PARAM_VARIANT;
	@ConfigurationParameter(name = PARAM_VARIANT, mandatory = false)
	protected String variant;

	/**
	 * Use this TreeTagger executable instead of trying to locate the executable
	 * automatically.
	 */
	public static final String PARAM_EXECUTABLE_PATH = "executablePath";
	@ConfigurationParameter(name = PARAM_EXECUTABLE_PATH, mandatory = false)
	private File executablePath;

	/**
	 * Load the model from this location instead of locating the model
	 * automatically.
	 */
	public static final String PARAM_MODEL_LOCATION = ComponentParameters.PARAM_MODEL_LOCATION;
	@ConfigurationParameter(name = PARAM_MODEL_LOCATION, mandatory = false)
	protected String modelLocation;

	/**
	 * The character encoding used by the model.
	 */
	public static final String PARAM_MODEL_ENCODING = ComponentParameters.PARAM_MODEL_ENCODING;
	@ConfigurationParameter(name = PARAM_MODEL_ENCODING, mandatory = false)
	protected String modelEncoding;

	/**
	 * Load the part-of-speech tag to UIMA type mapping from this location
	 * instead of locating the mapping automatically.
	 */
	public static final String PARAM_POS_MAPPING_LOCATION = ComponentParameters.PARAM_POS_MAPPING_LOCATION;
	@ConfigurationParameter(name = PARAM_POS_MAPPING_LOCATION, mandatory = false)
	protected String posMappingLocation;

	/**
	 * Use the {@link String#intern()} method on tags. This is usually a good
	 * idea to avoid spaming the heap with thousands of strings representing
	 * only a few different tags.
	 *
	 * Default: {@code true}
	 */
	public static final String PARAM_INTERN_TAGS = ComponentParameters.PARAM_INTERN_TAGS;
	@ConfigurationParameter(name = PARAM_INTERN_TAGS, mandatory = false, defaultValue = "true")
	private boolean internTags;

	/**
	 * Log the tag set(s) when a model is loaded.
	 *
	 * Default: {@code false}
	 */
	public static final String PARAM_PRINT_TAGSET = ComponentParameters.PARAM_PRINT_TAGSET;
	@ConfigurationParameter(name = PARAM_PRINT_TAGSET, mandatory = true, defaultValue = "false")
	protected boolean printTagSet;

	/**
	 * TT4J setting: Disable some sanity checks, e.g. whether tokens contain
	 * line breaks (which is not allowed). Turning this on will increase your
	 * performance, but the wrapper may throw exceptions if illegal data is
	 * provided.
	 */
	public static final String PARAM_PERFORMANCE_MODE = "performanceMode";
	@ConfigurationParameter(name = PARAM_PERFORMANCE_MODE, mandatory = true, defaultValue = "false")
	private boolean performanceMode;

	/**
	 * Write part-of-speech information.
	 *
	 * Default: {@code true}
	 */
	public static final String PARAM_WRITE_POS = ComponentParameters.PARAM_WRITE_POS;
	@ConfigurationParameter(name = PARAM_WRITE_POS, mandatory = true, defaultValue = "true")
	private boolean writePos;

	/**
	 * Write lemma information.
	 *
	 * Default: {@code true}
	 */
	public static final String PARAM_WRITE_LEMMA = ComponentParameters.PARAM_WRITE_LEMMA;
	@ConfigurationParameter(name = PARAM_WRITE_LEMMA, mandatory = true, defaultValue = "true")
	private boolean writeLemma;

	private CasConfigurableProviderBase<TreeTaggerWrapper<Token>> modelProvider;
	private MappingProvider posMappingProvider;

	final Logger logger = LoggerFactory.getLogger(TreeTagger.class);

	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);

		modelProvider = new ModelProviderBase<TreeTaggerWrapper<Token>>() {
			private TreeTaggerWrapper<Token> treetagger;

			{
				setContextObject(TreeTagger.this);

				setDefault(ARTIFACT_ID, "${groupId}.treetagger-model-tagger-${language}-${variant}");
				// setDefault(LOCATION,
				// "classpath:/${package}/lib/tagger-${language}-${variant}.properties");
				setDefault(LOCATION, "/home/selman/Software/Libraries/tree-tagger/lib/dutch-utf8.par");
				try {
					System.out.println(getModelLocation());
				} catch (Exception e) {
					System.out.println(e);
				}
//				setDefaultVariantsLocation("de/tudarmstadt/ukp/dkpro/core/treetagger/lib/tagger-default-variants.map");
				setDefaultVariantsLocation("classpath:/de/tudarmstadt/ukp/dkpro/core/api/lexmorph/tagset/nl-tt-pos.map");
				setDefault(VARIANT, "le"); // le = little-endian
				setDefault(SHARABLE, "true");

				setOverride(LOCATION, modelLocation);
				setOverride(LANGUAGE, language);
				setOverride(VARIANT, variant);
				setOverride(SHARABLE, "true");

				treetagger = new TreeTaggerWrapper<Token>();
				treetagger.setPerformanceMode(performanceMode);
				DKProExecutableResolver executableProvider = new DKProExecutableResolver(treetagger);
				executableProvider.setExecutablePath(executablePath);
				treetagger.setExecutableProvider(executableProvider);
				treetagger.setAdapter(new TokenAdapter<Token>() {
					@Override
					public String getText(Token aObject) {
						synchronized (aObject.getCAS()) {
							return aObject.getCoveredText();
						}
					}
				});
			}

			@Override
			protected TreeTaggerWrapper<Token> produceResource(URL aUrl) throws IOException {
				Properties meta = getResourceMetaData();
				String encoding = modelEncoding != null ? modelEncoding : meta.getProperty("encoding");
				String tagset = meta.getProperty("pos.tagset");

				File modelFile = ResourceUtils.getUrlAsFile(aUrl, true);

				// Reconfigure tagger
				// treetagger.setModel(modelFile.getPath() + ":" + encoding);
				treetagger.setModel("/home/selman/Software/Libraries/tree-tagger/lib/dutch-utf8.par");

				// Get tagset
				List<String> tags = TreeTaggerModelUtil.getTagset(modelFile, encoding);
				SingletonTagset posTags = new SingletonTagset(POS.class, tagset);
				posTags.addAll(tags);
				addTagset(posTags);

				if (printTagSet) {
					getContext().getLogger().log(INFO, getTagset().toString());
				}

				return treetagger;
			}
		};

		posMappingLocation = "classpath:/de/tudarmstadt/ukp/dkpro/core/api/lexmorph/tagset/nl-tt-pos.map";
		System.out.println(posMappingLocation + ", " + language + ", " + modelProvider);
		posMappingProvider = MappingProviderFactory.createPosMappingProvider(posMappingLocation, language,
				modelProvider);
		posMappingProvider.setDefault(MappingProvider.SHARABLE, "true");
		posMappingProvider.setOverride(MappingProvider.SHARABLE, "true");
		System.setProperty("treetagger.home", "/home/selman/Software/Libraries/tree-tagger/");
	}

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {
		modelProvider.configure(jCas.getCas());
		posMappingProvider.configure(jCas.getCas());

		final CAS cas = jCas.getCas();
		List<Token> tokens = new ArrayList<>(JCasUtil.select(jCas, Token.class));

		List<String> strings = new ArrayList<String>();
		Map<String, Token> strToTokMap = new HashMap<String, Token>();
		for (Token token : tokens) {
			String tokenText = token.getCoveredText();
			strings.add(tokenText);
			strToTokMap.put(tokenText, token);
		}

		TreeTaggerWrapper<String> tt = new TreeTaggerWrapper<String>();
		tt.setPerformanceMode(true);

		try {
			final AtomicInteger count = new AtomicInteger(0);
			final POS pos[] = new POS[tokens.size()];
			final Lemma lemma[] = new Lemma[tokens.size()];

			tt.setModel("/home/selman/Software/Libraries/tree-tagger/lib/dutch-utf8.par");
			tt.setHandler(new TokenHandler<String>() {
				@Override
				public void token(String aToken, String aPos, String aLemma) {
					System.out.println("token! " + aToken);
					synchronized (cas) {
						Token token = strToTokMap.get(aToken);
						// Add the Part of Speech
						if (writePos && aPos != null) {
							Type posTag = posMappingProvider.getTagType(aPos);
							POS posAnno = (POS) cas.createAnnotation(posTag, token.getBegin(), token.getEnd());
							posAnno.setPosValue(internTags ? aPos.intern() : aPos);
							// posAnno.setCoarseValue(posAnno.getClass().equals(POS.class)
							// ? null
							// : posAnno.getType().getShortName().intern());
							token.setPos(posAnno);
							pos[count.get()] = posAnno;
						}

						// Add the lemma
						if (writeLemma && aLemma != null) {
							Lemma lemmaAnno = new Lemma(jCas, token.getBegin(), token.getEnd());
							lemmaAnno.setValue(internTags ? aLemma.intern() : aLemma);
							token.setLemma(lemmaAnno);
							lemma[count.get()] = lemmaAnno;
						}

						count.getAndIncrement();
					}
				}
			});
			tt.process(strings);

			// Add the annotations to the indexes
			for (int i = 0; i < count.get(); i++) {
				if (pos[i] != null) {
					pos[i].addToIndexes();
				}
				if (lemma[i] != null) {
					lemma[i].addToIndexes();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (TreeTaggerException e) {
			e.printStackTrace();
		}
	}

}
