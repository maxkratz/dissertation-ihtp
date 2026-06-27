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
		Observer.getInstance().setCurrentSeries("Eval");
		final SingleMeasurement totalMeasurement = new SingleMeasurement();
		totalMeasurement.start();

		//
		// Convert JSON input file to XMI file
		//

		final SingleMeasurement loadMeasurement = new SingleMeasurement();
		loadMeasurement.start();

		if (verbose) {
			logger.info("=> Start JSON model loader.");
		}

		transformJsonToModel(inputPath, instancePath);
		loadMeasurement.stop();
		Observer.getInstance().addMeasurement("Eval", "LOAD_MODEL", loadMeasurement);
		logObserverMeasurement("LOAD_MODEL", verbose);

		//
		// Initialize GIPS API
		//

		final SingleMeasurement initMeasurement = new SingleMeasurement();
		initMeasurement.start();

		if (verbose) {
			logger.info("=> Start GIPS init.");
		}

		Observer.getInstance().setCurrentSeries("Eval");
		final SoftcnstrtuningGipsAPI gipsApi = new SoftcnstrtuningGipsAPI();
		XmiSetupUtil.checkIfEclipseOrJarSetup(gipsApi, instancePath);
		initMeasurement.stop();
		Observer.getInstance().addMeasurement("Eval", "INIT_GIPS", initMeasurement);
		logObserverMeasurement("INIT_GIPS", verbose);

		// Set GIPS configuration parameters from this object
		setGipsConfig(gipsApi);

		//
		// Run GIPS solution
		//

		buildAndSolve(gipsApi, verbose);

		//
		// Apply solution
		//

		final SingleMeasurement solutionApplicationMeasurement = new SingleMeasurement();
		solutionApplicationMeasurement.start();
		applySolution(gipsApi, verbose);
		solutionApplicationMeasurement.stop();

		Observer.getInstance().addMeasurement("Eval", "SOLUTION_APPLICATION", solutionApplicationMeasurement);
		logObserverMeasurement("SOLUTION_APPLICATION", verbose);

		//
		// GIPS save
		//

		final SingleMeasurement gipsSaveMeasurement = new SingleMeasurement();
		gipsSaveMeasurement.start();

		gipsSave(gipsApi, gipsOutputPath);
		gipsSaveMeasurement.stop();
		Observer.getInstance().addMeasurement("Eval", "GIPS_SAVE", gipsSaveMeasurement);
		logObserverMeasurement("GIPS_SAVE", verbose);

		//
		// Export
		//

		final SingleMeasurement exportMeasurement = new SingleMeasurement();
		exportMeasurement.start();

		if (verbose) {
			logger.info("=> Start JSON export.");
		}
		exportToJson(gipsOutputPath, outputPath);
		exportMeasurement.stop();
		Observer.getInstance().addMeasurement("Eval", "EXPORT", exportMeasurement);
		logObserverMeasurement(callbackPath, verbose);

		//
		// The end
		//

		gipsApi.terminate();
		totalMeasurement.stop();
		Observer.getInstance().addMeasurement("Eval", "TOTAL", totalMeasurement);
		logObserverMeasurement("TOTAL", verbose);
	}

}
