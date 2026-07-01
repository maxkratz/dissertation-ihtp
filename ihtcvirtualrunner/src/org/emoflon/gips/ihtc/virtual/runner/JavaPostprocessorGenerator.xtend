package org.emoflon.gips.ihtc.virtual.runner

import org.eclipse.emf.ecore.EPackage
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EAnnotation
import org.eclipse.emf.ecore.EReference
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.ArrayList
import java.util.List
import java.util.HashMap

/**
 * Generates a FULLY GENERIC Java Postprocessor based on ecore annotations.
 * 
 */
class JavaPostprocessorGenerator {
    
    def static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Program missing arguments. Arguments should be:")
            System.err.println("1. Output package (e.g org.emoflon.gips.ihtc.virtual.postprocessor)")
            System.err.println("2. Output file path")
            System.exit(1)
        }
        
        val outputPackage = args.get(0)
        val outputFilePath = args.get(1)
        
        val generator = new JavaPostprocessorGenerator()
        generator.generate(outputPackage, outputFilePath)
    }
    
    def void generate(String outputPackage, String outputFilePath) throws Exception {
        println("JavaPostprocessor Generator")
        println()
        
        println("Loading metamodel...")
        val ePackage = loadEcoreMetamodel()
        println("Metamodel loaded")
        println()
        
        println("Finding virtual node classes...")
        val virtualNodeClasses = findVirtualNodeClasses(ePackage)
        println("Found " + virtualNodeClasses.size() + " virtual node class(es):")
        virtualNodeClasses.forEach[cn | println("    - " + cn.name)]
        println()
        
        println("Generating JavaPostprocessor class...")
        val generatedCode = generatePostprocessor(outputPackage)
        println("Code generated")
        println()
        
        println("Writing to file...")
        println("Output: " + outputFilePath)
        val outputFile = new File(outputFilePath)
        outputFile.parentFile.mkdirs()
        Files.write(Paths.get(outputFilePath), generatedCode.bytes)
        println("File written successfully")
        println()
        
        println("Generation complete.")
    }
    
    private def List<EClass> findVirtualNodeClasses(EPackage ePackage) {
        val virtualNodeClasses = new ArrayList<EClass>()
        ePackage.getEClassifiers().forEach [ classifier |
            if (classifier instanceof EClass) {
                val eClass = classifier as EClass
                if (eClass.getEAnnotation("virtualNode") !== null) {
                    virtualNodeClasses.add(eClass)
                }
            }
        ]
        return virtualNodeClasses
    }
    
    
    def String generatePostprocessor(String packageName) {
        
        '''
        package «packageName»;
        
        import java.io.IOException;
        import java.util.Objects;
        import java.util.logging.ConsoleHandler;
        import java.util.logging.Formatter;
        import java.util.logging.LogRecord;
        import java.util.logging.Logger;
        import java.util.ArrayList;
        import java.util.List;
        
        import org.eclipse.emf.common.util.EList;
        import org.eclipse.emf.ecore.EObject;
        import org.eclipse.emf.ecore.EStructuralFeature;
        import org.emoflon.smartemf.persistence.SmartEMFResourceFactoryImpl;
        import org.emoflon.smartemf.runtime.collections.LinkedSmartESet;
        import org.emoflon.smartemf.runtime.collections.SmartESet;
        
        import ihtcvirtualmetamodel.IhtcvirtualmetamodelPackage;
        import ihtcvirtualmetamodel.Root;
        import ihtcvirtualmetamodel.utils.FileUtils;
        import ihtcvirtualmetamodel.*;
        
        /**
         * Auto-generated Virtual Node Post-Processor.
         * 
         * Algorithm:
         *   For each virtual node type:
         *     - Navigate to container class (idenitfied from ecore)
         *     - Access virtual node
         *     - For each selected node:
         *       - Get source/target
         *       - Create derived edges
         *       - Remove virtual node from container
         */
        public class JavaPostprocessor {
            
            protected final Logger logger = Logger.getLogger(JavaPostprocessor.class.getName());
            
            private final String xmiInputFilePath;
            private final String xmiOutputFilePath;
            private Root model;
            
            public JavaPostprocessor(final String xmiInputFilePath, final String xmiOutputFilePath) {
                Objects.requireNonNull(xmiInputFilePath);
                Objects.requireNonNull(xmiOutputFilePath);
                
                this.xmiInputFilePath = xmiInputFilePath;
                this.xmiOutputFilePath = xmiOutputFilePath;
                
                logger.setUseParentHandlers(false);
                final ConsoleHandler handler = new ConsoleHandler();
                handler.setFormatter(new Formatter() {
                    @Override
                    public String format(final LogRecord record) {
                        return record.getMessage() + System.lineSeparator();
                    }
                });
                logger.addHandler(handler);
            }
            
            /**
             * Main execution method.
             */
            public void run() {
                try {
                    logger.info("  Virtual Node Post-Processing (Generic)");
                    
                    logger.info("Loading model from: " + xmiInputFilePath);
                    model = loadModel(xmiInputFilePath);
                    logger.info("Model loaded");
                    
                    logger.info("Processing virtual nodes...");
                   
                } catch (final IOException e) {
                    logger.warning("IOException: " + e.getMessage());
                    e.printStackTrace();
                    System.exit(1);
                } catch (final Exception e) {
                    logger.warning("Error: " + e.getMessage());
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            
            private Root loadModel(final String path) throws IOException {
                Objects.requireNonNull(path);
                final org.eclipse.emf.ecore.resource.ResourceSet rs = 
                    new org.eclipse.emf.ecore.resource.impl.ResourceSetImpl();
                final org.eclipse.emf.ecore.resource.Resource.Factory.Registry reg = 
                    org.eclipse.emf.ecore.resource.Resource.Factory.Registry.INSTANCE;
                reg.getExtensionToFactoryMap().put("xmi", new SmartEMFResourceFactoryImpl("../"));
                rs.getPackageRegistry().put(
                    IhtcvirtualmetamodelPackage.eNS_URI, 
                    IhtcvirtualmetamodelPackage.eINSTANCE
                );
                final org.eclipse.emf.ecore.resource.Resource resource = rs.getResource(
                    org.eclipse.emf.common.util.URI.createFileURI(path), true
                );
                return (Root) resource.getContents().get(0);
            }
            
            private void saveModel(final Root model, final String path) throws IOException {
                Objects.requireNonNull(model);
                Objects.requireNonNull(path);
                FileUtils.save(model, path);
            }
            
        }
        '''
    }
    
    
    private def EPackage loadEcoreMetamodel() throws IOException {
        val ResourceSet resourceSet = new ResourceSetImpl()
        resourceSet.getResourceFactoryRegistry()
            .getExtensionToFactoryMap()
            .put("ecore", new EcoreResourceFactoryImpl())
        
        val String ecorePath = "../ihtcvirtualmetamodel/model/Ihtcvirtualmetamodel.ecore"
        val File ecoreFile = new File(ecorePath)
        
        if (!ecoreFile.exists()) {
            throw new IOException("File not found: " + ecoreFile.getAbsolutePath())
        }
        
        val URI uri = URI.createFileURI(ecoreFile.getAbsolutePath())
        val Resource resource = resourceSet.getResource(uri, true)
        
        return resource.getContents().get(0) as EPackage
    }
}
