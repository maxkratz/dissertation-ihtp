package ihtcvirtualpostprocessing;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.emoflon.ibex.gt.api.GraphTransformationPattern;
import org.emoflon.ibex.gt.api.GraphTransformationRule;
import org.emoflon.smartemf.persistence.SmartEMFResourceFactoryImpl;

import ihtcvirtualmetamodel.IhtcvirtualmetamodelPackage;
import ihtcvirtualmetamodel.Root;
import ihtcvirtualmetamodel.utils.FileUtils;
import ihtcvirtualpostprocessing.api.IhtcvirtualpostprocessingAPI;
import ihtcvirtualpostprocessing.api.IhtcvirtualpostprocessingHiPEApp;

/**
 * This eMoflon::IBeX-GT app can be used to run all post-processing rules of the
 * defined GT rule set on a given model.
 * 
 * @author Maximilian Kratz (maximilian.kratz@es.tu-darmstadt.de)
 */
public class PostprocessingGtApp extends IhtcvirtualpostprocessingHiPEApp {

	/**
	 * Logger for system outputs.
	 */
	protected final Logger logger = Logger.getLogger(PostprocessingGtApp.class.getName());

	/**
	 * Global limit of the number of GT rule applications per GT rule.
	 */
	private static final int GT_RULE_APPLICATION_LIMIT = 40_000;

	// TODO: This field may be removed.
	/**
	 * XMI model input file path. This value will be used to read the input model.
	 */
	@Deprecated
	private final String xmiInputFilePath;

	/**
	 * XMI model output file path. This value will be used to write the output model
	 * to.
	 */
	private final String xmiOutputFilePath;

	/**
	 * Creates a new instance of the pre-processing GT app. The given
	 * `xmiInputFilePath` will be used as input file path. The given
	 * `xmiOutputFilePath` will be used as output file path.
	 * 
	 * @param xmiInputFilePath  Input file path.
	 * @param xmiOutputFilePath Output file path.
	 */
	public PostprocessingGtApp(final String xmiInputFilePath, final String xmiOutputFilePath) {
		super(EmoflonGtAppUtils.createTempDir().normalize().toString() + "/");
		Objects.requireNonNull(xmiInputFilePath);
		Objects.requireNonNull(xmiOutputFilePath);
		EmoflonGtAppUtils.extractFiles(workspacePath);

		// Load model from given XMI file path
		Root hospital = null;
		try {
			hospital = loadModel(xmiInputFilePath);
		} catch (final IOException e) {
			logger.warning("IOException occurred while reading the input XMI file." + e.getMessage());
			System.exit(1);
		}

		// Proceed with the app creation
		if (hospital.eResource() == null) {
			createModel(URI.createURI(xmiInputFilePath));
			resourceSet.getResources().get(0).getContents().add(hospital);
		} else {
			resourceSet = hospital.eResource().getResourceSet();
		}

		this.xmiInputFilePath = xmiInputFilePath;
		this.xmiOutputFilePath = xmiOutputFilePath;

		// Configure logging
		logger.setUseParentHandlers(false);
		final ConsoleHandler handler = new ConsoleHandler();
		handler.setFormatter(new Formatter() {
			@Override
			public String format(final LogRecord record) {
				Objects.requireNonNull(record, "Given log entry was null.");
				return record.getMessage() + System.lineSeparator();
			}
		});
		logger.addHandler(handler);
	}

	/**
	 * Executes the GT rules of this app according to the configuration.
	 */
	public void run() {
		// Create the API object
		final IhtcvirtualpostprocessingAPI api = this.initAPI();

		// Update matches
		api.updateMatches();

		// Apply all GT rule matches until the specified limit hits
		final Map<String, GtRuleApplication> applicationStats = new HashMap<String, GtRuleApplication>();
		int currentNumberOfApplications = 1;
		while (currentNumberOfApplications > 0) {
			currentNumberOfApplications = 0;
			for (var entry : api.getAllPatterns().entrySet()) {
				final String ruleName = entry.getKey();
				final GraphTransformationPattern<?, ?> pattern = entry.getValue().get();
				if (pattern instanceof GraphTransformationRule rule) {
					logger.info("Applying rule: " + ruleName);
					final GtRuleApplication stats = applyMatches(rule, GT_RULE_APPLICATION_LIMIT, 0);
					currentNumberOfApplications += stats.getLastNumberOfAppliedMatches();

					// Save stats increment to lookup structure
					if (!applicationStats.containsKey(ruleName)) {
						applicationStats.put(ruleName, stats);
					} else {
						applicationStats.get(ruleName).merge(stats);
					}
				}
			}
		}
		logger.info(this.getClass().getSimpleName() + ": I finished applying GT rules.");

		// Print statistics
		for (final String name : applicationStats.keySet()) {
			logger.info(this.getClass().getSimpleName() + ": Initial number of matches of GT rule " + name + " "
					+ applicationStats.get(name).getInitialNumberOfMatches() + ".");
			logger.info(this.getClass().getSimpleName() + ": I applied the GT rule " + name + " "
					+ applicationStats.get(name).getNumberOfAppliedMatches() + " times.");
			logger.info(this.getClass().getSimpleName() + ": Remaining number of matches of GT rule " + name + " "
					+ applicationStats.get(name).getRemainingNumberOfMatches() + ".");
		}

		// Persist model to XMI output path
		try {
			logger.info("Started writing the XMI file.");
			final Resource res = api.getModel().getResources().get(0);
			Objects.requireNonNull(res);
			FileUtils.save((Root) res.getContents().get(0), xmiOutputFilePath);
		} catch (final IOException e) {
			logger.warning("IOException occurred while writing the output XMI file." + e.getMessage());
			System.exit(1);
		}

		// Terminate the eMoflon::IBeX-GT (HiPE) API
		api.terminate();
	}

	//
	// Utility methods.
	//

	/**
	 * Applies the given GT rule until it either does not have any more matches or
	 * the global GT rule application limit was hit.
	 * 
	 * @param rule            GT rule to apply.
	 * @param limit           Maximum number of GT rule applications.
	 * @param previousApplied Number of previous GT rule applications.
	 * @return Statistics of the applied matches.
	 */
	private GtRuleApplication applyMatches(final GraphTransformationRule<?, ?> rule, final int limit,
			final int previousApplied) {
		final int inital = (int) rule.countMatches();
		int counter = 0;
		// doUpdate = false to not run the PM on every pass of the loop
		while (rule.isApplicable(false)) {
			if (counter + previousApplied >= limit) {
				break;
			}
			// doUpdate = false to not run the PM on every pass of the loop
			rule.apply(false);
			counter++;
		}
		return new GtRuleApplication(inital, counter, (int) rule.countMatches());
	}

	/**
	 * Loads the used model as XMI file from the given file path.
	 * 
	 * @param path File path from which the model should be read as XMI file.
	 * @throws IOException If an IOException occurs during read, this method will
	 *                     pass it.
	 */
	private Root loadModel(final String path) throws IOException {
		Objects.requireNonNull(path);

		final ResourceSet rs = new ResourceSetImpl();
		final Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
		reg.getExtensionToFactoryMap().put("xmi", new SmartEMFResourceFactoryImpl("../"));
		rs.getPackageRegistry().put(IhtcvirtualmetamodelPackage.eNS_URI, IhtcvirtualmetamodelPackage.eINSTANCE);
		final Resource model = rs.getResource(URI.createFileURI(path), true);
		return (Root) model.getContents().get(0);
	}

	public class GtRuleApplication {
		int initialNumberOfMatches = 0;
		int numberOfAppliedMatches = 0;
		int remainingNumberOfMatches = 0;

		int lastNumberOfAppliedMatches = 0;

		public GtRuleApplication(final int initial, final int applications, int remaining) {
			this.initialNumberOfMatches = initial;
			this.numberOfAppliedMatches = applications;
			this.remainingNumberOfMatches = remaining;
			this.lastNumberOfAppliedMatches = applications;
		}

		public void merge(final GtRuleApplication other) {
			// Do not overwrite initial number of matches
			// Increment number of applied matches
			this.numberOfAppliedMatches += other.getNumberOfAppliedMatches();
			// Overwrite remaining number of matches
			this.remainingNumberOfMatches = other.getRemainingNumberOfMatches();
			// Save last number of applied matches
			this.lastNumberOfAppliedMatches = other.getNumberOfAppliedMatches();
		}

		public int getInitialNumberOfMatches() {
			return this.initialNumberOfMatches;
		}

		public int getNumberOfAppliedMatches() {
			return this.numberOfAppliedMatches;
		}

		public int getRemainingNumberOfMatches() {
			return this.remainingNumberOfMatches;
		}

		public int getLastNumberOfAppliedMatches() {
			return this.lastNumberOfAppliedMatches;
		}
	}

}
