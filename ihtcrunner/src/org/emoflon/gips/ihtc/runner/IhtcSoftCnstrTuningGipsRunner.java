package org.emoflon.gips.ihtc.runner;

import java.util.Objects;

import org.emoflon.gips.core.util.Observer;
import org.emoflon.gips.core.util.SingleMeasurement;
import org.emoflon.gips.ihtc.runner.utils.XmiSetupUtil;

import ihtcgipssolution.softcnstrtuning.api.gips.SoftcnstrtuningGipsAPI;

/**
 * This concrete runner contains utility methods to wrap a given GIPS API object
 * in the context of the IHTC 2024 example. This implementation takes all hard
 * constraints as well as three selected soft constraints into account.
 * 
 * @author Maximilian Kratz (maximilian.kratz@es.tu-darmstadt.de)
 */
public class IhtcSoftCnstrTuningGipsRunner extends AbstractIhtcGipsRunner {

	/**
	 * Create a new instance of this class.
	 */
	public IhtcSoftCnstrTuningGipsRunner() {
	}

	/**
	 * Main method to execute the runner. Arguments will be ignored.
	 * 
	 * @param args Arguments will be ignored.
	 */
	public static void main(final String[] args) {
		Objects.requireNonNull(args);

		final IhtcSoftCnstrTuningGipsRunner runner = new IhtcSoftCnstrTuningGipsRunner();
		runner.setupDefaultPaths();
		runner.run();
	}

	@Override
	public void run() {
		checkIfFileExists(inputPath);
		final Observer observer = new Observer();

		final SingleMeasurement totalMeasurement = new SingleMeasurement();
		totalMeasurement.start();

		observer.singleMeasurement("Eval", "TOTAL", () -> {
			//
			// Convert JSON input file to XMI file
			//

			if (verbose) {
				logger.info("=> Start JSON model loader.");
			}

			observer.singleMeasurement("Eval", "LOAD_MODEL", () -> {
				transformJsonToModel(inputPath, instancePath);
			});
			logObserverMeasurement("LOAD_MODEL", verbose, observer.getStageMeasurements("Eval"));

			//
			// Initialize GIPS API
			//

			if (verbose) {
				logger.info("=> Start GIPS init.");
			}

			final SoftcnstrtuningGipsAPI gipsApi = observer.singleMeasurement("Eval", "INIT_GIPS", () -> {
				final SoftcnstrtuningGipsAPI api = new SoftcnstrtuningGipsAPI();
				XmiSetupUtil.checkIfEclipseOrJarSetup(api, instancePath);
				return api;
			});
			logObserverMeasurement("INIT_GIPS", verbose, observer.getStageMeasurements("Eval"));

			// Set GIPS configuration parameters from this object
			setGipsConfig(gipsApi);

			//
			// Run GIPS solution
			//

			buildAndSolve(gipsApi, verbose);

			//
			// Apply solution
			//

			observer.singleMeasurement("Eval", "SOLUTION_APPLICATION", () -> {
				applySolution(gipsApi, verbose);
			});
			logObserverMeasurement("SOLUTION_APPLICATION", verbose, observer.getStageMeasurements("Eval"));

			//
			// GIPS save
			//

			observer.singleMeasurement("Eval", "GIPS_SAVE", () -> {
				gipsSave(gipsApi, gipsOutputPath);
			});
			logObserverMeasurement("GIPS_SAVE", verbose, observer.getStageMeasurements("Eval"));

			//
			// Export
			//

			if (verbose) {
				logger.info("=> Start JSON export.");
			}

			observer.singleMeasurement("Eval", "EXPORT", () -> {
				exportToJson(gipsOutputPath, outputPath);
			});

			logObserverMeasurement("EXPORT", verbose, observer.getStageMeasurements("Eval"));

			//
			// The end
			//

			gipsApi.terminate();
		});
		logObserverMeasurement("TOTAL", verbose, observer.getStageMeasurements("Eval"));
	}

}
