package org.emoflon.gips.ihtc.virtual.runner;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.resource.Resource;
import org.emoflon.gips.core.GipsMapper;
import org.emoflon.gips.core.util.IMeasurement;
import org.emoflon.gips.core.util.Observer;
import org.emoflon.gips.core.util.SingleMeasurement;
import org.emoflon.gips.ihtc.virtual.runner.utils.FileUtils;
import org.emoflon.gips.ihtc.virtual.runner.utils.XmiSetupUtil;

import ihtcvirtualgipssolution.api.gips.IhtcvirtualgipssolutionGipsAPI;
import ihtcvirtualmetamodel.Root;
import ihtcvirtualmetamodel.importexport.ModelToJsonExporter;
import ihtcvirtualmetamodel.importexport.ModelToJsonNoPostProcExporter;
import ihtcvirtualmetamodel.importexport.SolvedModelValidator;

public class IhtcVirtualGipsRunner extends AbstractIhtcVirtualGipsRunner {

	/**
	 * If true, the runner will print more detailed information.
	 */
	private boolean verbose = true;

	/**
	 * If true, the post processing will be skipped and the JSON output will be
	 * directly derived from the GIPS solution model (i.e., it will search for
	 * `isSelected == true` virtual objects.
	 */
	private boolean postProc = false;

	/**
	 * If true, the pre-processing will be executed with the Java-only (i.e., no GT)
	 * implementation.
	 */
	private boolean preProcNoGt = true;

	/**
	 * If true, the application of the GT rules of the GIPSL specification will only
	 * be simulated by manually written Java code instead of actually applying GT
	 * rule matches with eMoflon::IBeX-GT.
	 */
	private boolean applicationNoGt = false;

	/**
	 * If true a timestamp will be added to the filename.
	 */
	private boolean saveAllDebugFiles = true;

	/**
	 * Random seed for the (M)ILP solver.
	 */
	private int randomSeed = 0;

	/**
	 * Time limit for the (M)ILP solver.
	 */
	private int solveTimeLimit = -1;

	/**
	 * Time limit for the GIPS build process.
	 */
	private int buildTimeLimit = -1;

	/**
	 * Number of threads for the (M)ILP solver.
	 */
	private int threads = 0;

	/**
	 * Gurobi callback path.
	 */
	private String callbackPath = projectFolder + "/../ihtcvirtualrunner/scripts/callback.json";

	/**
	 * Gurobi parameter path.
	 */
	private String parameterPath = projectFolder + "/../ihtcvirtualrunner/scripts/parameter.json";

	/**
	 * If true, the removal of redundant/useless constraints will be disabled.
	 */
	private boolean disableUselessConstraintRemoval = false;

	/**
	 * Create a new instance of this class.
	 */
	public IhtcVirtualGipsRunner() {
	}

	/**
	 * Main method to execute the runner. Arguments will be ignored.
	 * 
	 * @param args Arguments will be ignored.
	 */
	public static void main(final String[] args) {
		Objects.requireNonNull(args);

		final IhtcVirtualGipsRunner runner = new IhtcVirtualGipsRunner();
		runner.setupDefaultPaths();
		runner.run();
	}

	/**
	 * Sets the default paths up.
	 */
	void setupDefaultPaths() {
		// Update output JSON file path
		this.datasetSolutionFolder = projectFolder + "/../ihtcvirtualmetamodel/resources/runner/";
		this.outputPath = datasetSolutionFolder + "sol_"
				+ scenarioFileName.substring(0, scenarioFileName.lastIndexOf(".json")) + "_gips.json";
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
		if (verbose) {
			logger.info("Runtime model load: " + loadMeasurement.maxDurationSeconds() + "s.");
		}

		//
		// Pre-processing via a separated GT rule set
		//

		final SingleMeasurement preprocMeasurement = new SingleMeasurement();
		preprocMeasurement.start();

		if (verbose) {
			logger.info("=> Start pre-processing.");
		}

		if (preProcNoGt) {
			preprocessNoGt(instancePath, preprocessingPath);
		} else {
			preprocess(instancePath, preprocessingPath);
		}
		preprocMeasurement.stop();
		Observer.getInstance().addMeasurement("Eval", "PREPROC", preprocMeasurement);
		if (verbose) {
			logger.info("Runtime pre-processing: " + preprocMeasurement.maxDurationSeconds() + "s.");
		}

		//
		// Initialize GIPS API
		//

		final SingleMeasurement initMeasurement = new SingleMeasurement();
		initMeasurement.start();

		if (verbose) {
			logger.info("=> Start GIPS init.");
		}

		final IhtcvirtualgipssolutionGipsAPI gipsApi = new IhtcvirtualgipssolutionGipsAPI();
		XmiSetupUtil.checkIfEclipseOrJarSetup(gipsApi, preprocessingPath);
		initMeasurement.stop();
		Observer.getInstance().addMeasurement("Eval", "INIT_GIPS", initMeasurement);
		if (verbose) {
			logger.info("Runtime GIPS init: " + initMeasurement.maxDurationSeconds() + "s.");
		}

		// Set GIPS configuration parameters from this object
		setGipsConfig(gipsApi);

		//
		// Run GIPS solution
		//

		buildAndSolve(gipsApi, verbose, buildTimeLimit);

		//
		// Apply solution
		//

		final SingleMeasurement solutionApplicationMeasurement = new SingleMeasurement();
		solutionApplicationMeasurement.start();

		if (applicationNoGt) {
			applySolutionNoGt(gipsApi, verbose);
		} else {
			applySolution(gipsApi, verbose);
		}

		solutionApplicationMeasurement.stop();
		Observer.getInstance().addMeasurement("Eval", "SOLUTION_APPLICATION", solutionApplicationMeasurement);
		if (verbose) {
			logger.info("Runtime solution application: " + solutionApplicationMeasurement.maxDurationSeconds() + "s.");
		}

		// Print variable statistics for all mappers
		if (verbose) {
			logger.info("=> Print variable statistics (estimation): ");
			int totalVars = 0;

			// GT rule-based mappings
			totalVars += logVarStats(gipsApi.getSelectedShiftToFirstWorkload());
			totalVars += logVarStats(gipsApi.getSelectedExtendingShiftToWorkload());
			totalVars += logVarStats(gipsApi.getSelectedOccupantNodes());
			totalVars += logVarStats(gipsApi.getSelectedOperationDay());
			totalVars += logVarStats(gipsApi.getSelectedShiftToRoster());

			// utility mappings
			totalVars += logVarStats(gipsApi.getAssignedPatientsToRoom());
			totalVars += logVarStats(gipsApi.getAssignedGenderToRoomOnShift());
			totalVars += logVarStats(gipsApi.getOpenOTs());
			totalVars += logVarStats(gipsApi.getSurgeonInOT());
			totalVars += logVarStats(gipsApi.getSurgeonPenalizedOTs());
			totalVars += logVarStats(gipsApi.getAgeGroupsInRoom());
			totalVars += logVarStats(gipsApi.getAssignedNursesToWorkload());
			totalVars += logVarStats(gipsApi.getNurseWorkloadForDay());
			totalVars += logVarStats(gipsApi.getAssignedNurseForPatient());

			logger.info("Total estimated number of variables: " + totalVars);
		}

		//
		// GIPS save
		//

		final SingleMeasurement gipsSaveMeasurement = new SingleMeasurement();
		gipsSaveMeasurement.start();

		gipsSave(gipsApi, gipsOutputPath);
		gipsSaveMeasurement.stop();
		Observer.getInstance().addMeasurement("Eval", "GIPS_SAVE", gipsSaveMeasurement);
		if (verbose) {
			logger.info("Runtime GIPS save: " + gipsSaveMeasurement.maxDurationSeconds() + "s.");
		}

		//
		// Model Validation
		//

		final SingleMeasurement modelValidateMeasurement = new SingleMeasurement();
		modelValidateMeasurement.start();

		if (verbose) {
			logger.info("=> Start Model Validation");
		}

		validateModel(gipsOutputPath);
		modelValidateMeasurement.stop();
		Observer.getInstance().addMeasurement("Eval", "MODEL_VALIDATE", modelValidateMeasurement);
		if (verbose) {
			logger.info("Runtime validate model: " + modelValidateMeasurement.maxDurationSeconds() + "s.");
		}

		//
		// Export
		//

		final SingleMeasurement exportMeasurement = new SingleMeasurement();
		exportMeasurement.start();

		if (verbose) {
			logger.info("=> Start JSON export.");
		}

		if (postProc) {
			final SingleMeasurement postProcMeasurement = new SingleMeasurement();
			postProcMeasurement.start();
			if (verbose) {
				logger.info("=> Start post-processing GT.");
			}
			postprocess(gipsOutputPath, postProcOutputPath);
			postProcMeasurement.stop();
			Observer.getInstance().addMeasurement("Eval", "POSTPROC", postProcMeasurement);
			if (verbose) {
				logger.info("Runtime post-processing: " + postProcMeasurement.maxDurationSeconds() + "s.");
			}
			exportToJson(postProcOutputPath, outputPath);
		} else {
			if (verbose) {
				logger.info("=> Skipped post-processing GT.");
			}
			exportToJsonNoPostProc(gipsOutputPath, outputPath);
		}
		exportMeasurement.stop();
		Observer.getInstance().addMeasurement("Eval", "EXPORT", exportMeasurement);
		if (verbose) {
			logger.info("Runtime export (total): " + exportMeasurement.maxDurationSeconds() + "s.");
		}

		//
		// The end
		//

		gipsApi.terminate();
		totalMeasurement.stop();
		Observer.getInstance().addMeasurement("Eval", "TOTAL", totalMeasurement);
		if (verbose) {
			logger.info("Runtime total: " + totalMeasurement.maxDurationSeconds() + "s.");
		}

		// Print all observer measurements
		if (verbose) {
			final Map<String, IMeasurement> measurements = new LinkedHashMap<>(
					Observer.getInstance().getMeasurements("Eval"));
			// Start
			logger.info("LOAD_MODEL: " + measurements.get("LOAD_MODEL").maxDurationSeconds() + "s.");
			logger.info("PREPROC: " + measurements.get("PREPROC").maxDurationSeconds() + "s.");

			// GIPS
			logger.info("INIT_GIPS: " + measurements.get("INIT_GIPS").maxDurationSeconds() + "s.");
			logger.info("PM: " + measurements.get("PM").maxDurationSeconds() + "s.");
			logger.info("BUILD_GIPS: " + measurements.get("BUILD_GIPS").maxDurationSeconds() + "s.");
			logger.info("BUILD_SOLVER: " + measurements.get("BUILD_SOLVER").maxDurationSeconds() + "s.");
			logger.info("BUILD: " + measurements.get("BUILD").maxDurationSeconds() + "s.");
			logger.info("SOLVE_PROBLEM: " + measurements.get("SOLVE_PROBLEM").maxDurationSeconds() + "s.");
			logger.info(
					"SOLUTION_APPLICATION: " + measurements.get("SOLUTION_APPLICATION").maxDurationSeconds() + "s.");
			logger.info("GIPS_SAVE: " + measurements.get("GIPS_SAVE").maxDurationSeconds() + "s.");

			// End
			logger.info("MODEL_VALIDATE: " + measurements.get("MODEL_VALIDATE").maxDurationSeconds() + "s.");
			if (measurements.containsKey("POSTPROC")) {
				logger.info("POSTPROC: " + measurements.get("POSTPROC").maxDurationSeconds() + "s.");
			}
			logger.info("EXPORT: " + measurements.get("EXPORT").maxDurationSeconds() + "s.");
			logger.info("TOTAL: " + measurements.get("TOTAL").maxDurationSeconds() + "s.");
		}
	}

	/**
	 * Prints the estimated number of variables to be created by the given GIPS
	 * mapper.
	 * 
	 * @param mapper GIPS mapper to print the estimates number of variables for.
	 * @return Returns the total estimated number of variables.
	 */
	private int logVarStats(final GipsMapper<?> mapper) {
		Objects.requireNonNull(mapper);
		final int implicitVars = mapper.getMappings().size();
		logger.info(mapper.getMapping().getName() + ": " + implicitVars);
		final int additionalVars = mapper.getMapping().getFreeVariables().size();
		if (additionalVars > 0) {
			logger.info(mapper.getMapping().getName() + "_additional" + ": " + additionalVars * implicitVars);
		}
		return implicitVars + (additionalVars * implicitVars);
	}

	/**
	 * Takes a XMI output path (of a GIPS-generated solution model) and validates
	 * the model
	 * 
	 * @param gipsOutputPath
	 */
	private void validateModel(final String gipsOutputPath) {
		Objects.requireNonNull(gipsOutputPath);
		Objects.requireNonNull(debugOutputPath);
		final Resource loadedResource = FileUtils.loadModel(gipsOutputPath);
		final Root solvedHospital = (Root) loadedResource.getContents().get(0);
		final SolvedModelValidator validator = new SolvedModelValidator(solvedHospital, verbose);
		if (saveAllDebugFiles) {
			LocalDateTime now = LocalDateTime.now();
			String formatted = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
			String debugOutputPathTimeStamp = debugOutputPath.substring(0, debugOutputPath.lastIndexOf(".txt")) + "_"
					+ formatted + ".txt";
			validator.validate(debugOutputPathTimeStamp);
		} else {
			validator.validate(debugOutputPath);
		}
	}

	/**
	 * Takes an XMI output path (of a GIPS-generated solution model) and writes the
	 * corresponding JSON output to `jsonOutputPath`.
	 * 
	 * @param xmiOutputPath  GIPS-generated solution model to convert.
	 * @param jsonOutputPath JSON output file location to write the JSON output file
	 *                       to.
	 */
	private void exportToJson(final String xmiOutputPath, final String jsonOutputPath) {
		Objects.requireNonNull(xmiOutputPath);
		Objects.requireNonNull(jsonOutputPath);

		final Resource loadedResource = FileUtils.loadModel(xmiOutputPath);
		final Root solvedHospital = (Root) loadedResource.getContents().get(0);
		final ModelToJsonExporter exporter = new ModelToJsonExporter(solvedHospital);
		logger.info("Writing output JSON file to: " + outputPath);
		exporter.modelToJson(jsonOutputPath, verbose);
	}

	/**
	 * Takes an XMI output path (of a GIPS-generated solution model) and writes the
	 * corresponding JSON output to `jsonOutputPath`. This method relies on the
	 * non-post-processed model.
	 * 
	 * @param xmiOutputPath  GIPS-generated solution model to convert.
	 * @param jsonOutputPath JSON output file location to write the JSON output file
	 *                       to.
	 */
	private void exportToJsonNoPostProc(final String xmiOutputPath, final String jsonOutputPath) {
		Objects.requireNonNull(xmiOutputPath);
		Objects.requireNonNull(jsonOutputPath);

		final Resource loadedResource = FileUtils.loadModel(xmiOutputPath);
		final Root solvedHospital = (Root) loadedResource.getContents().get(0);
		final ModelToJsonNoPostProcExporter exporter = new ModelToJsonNoPostProcExporter(solvedHospital);
		logger.info("Writing output JSON file to: " + outputPath);
		exporter.modelToJson(jsonOutputPath, verbose);
	}

	/**
	 * Sets the verbose flag to the given value.
	 * 
	 * @param verbose Verbose flag value.
	 */
	public void setVerbose(final boolean verbose) {
		this.verbose = verbose;
	}

	/**
	 * Sets the random seed to the given value.
	 * 
	 * @param seed Random seed to set.
	 */
	public void setRandomSeed(final int seed) {
		this.randomSeed = seed;
	}

	/**
	 * Sets the (M)ILP solver time limit to the given value.
	 * 
	 * @param timeLimit Time limit to set.
	 */
	public void setSolveTimeLimit(final int timeLimit) {
		this.solveTimeLimit = timeLimit;
	}

	/**
	 * Sets the GIPS build time limit to the given value.
	 * 
	 * @param timeLimit Time limit to set.
	 */
	public void setBuildTimeLimit(final int timeLimit) {
		this.buildTimeLimit = timeLimit;
	}

	/**
	 * Sets the number of threads to be used by the (M)ILP solver.
	 * 
	 * @param threads Number of threads to set.
	 */
	public void setThreads(final int threads) {
		this.threads = threads;
	}

	/**
	 * Sets the Gurobi callback path to the given value.
	 * 
	 * @param callbackPath Gurobi callback path to set.
	 */
	public void setCallbackPath(final String callbackPath) {
		Objects.requireNonNull(callbackPath);
		this.callbackPath = callbackPath;
	}

	/**
	 * Sets the Gurobi parameter path to the given value.
	 * 
	 * @param parameterPath Gurobi parameter path to set.
	 */
	public void setParameterPath(final String parameterPath) {
		Objects.requireNonNull(parameterPath);
		this.parameterPath = parameterPath;
	}

	/**
	 * Sets the pre-processing approach to the given value.
	 * 
	 * @param noGt If true, the Java-based pre-processing will be used.
	 */
	public void setPreProcessingApproach(final boolean noGt) {
		this.preProcNoGt = noGt;
	}

	/**
	 * Sets the private GIPS API configuration parameters from this object to the
	 * actual GIPS API.
	 * 
	 * @param gipsApi GIPS API to set the configuration parameters for.
	 */
	private void setGipsConfig(final IhtcvirtualgipssolutionGipsAPI gipsApi) {
		Objects.requireNonNull(gipsApi);

		gipsApi.getSolverConfig().setRandomSeed(randomSeed);
		if (solveTimeLimit != -1) {
			gipsApi.getSolverConfig().setTimeLimit(solveTimeLimit);
		}
		gipsApi.getSolverConfig().setThreadCount(threads);
		if (callbackPath != null) {
			gipsApi.getSolverConfig().setEnableCallbackPath(true);
			gipsApi.getSolverConfig().setCallbackPath(callbackPath);
		}
		if (parameterPath != null) {
			gipsApi.getSolverConfig().setParameterPath(parameterPath);
		}
		gipsApi.getSolverConfig().setEnableOutput(verbose);
		gipsApi.getConfig().setPrintUselessConstraintsStats(true);
		gipsApi.getConfig().setUselessDuplicateConstraints(!disableUselessConstraintRemoval);
	}

	/**
	 * Sets the disabling of redundant/useless constraints to the given value.
	 * 
	 * @param disable If true, the removal of redundant/useless constraints will be
	 *                disabled.
	 */
	public void setDisableUselessConstraintRemoval(final boolean disable) {
		this.disableUselessConstraintRemoval = disable;
	}

}
