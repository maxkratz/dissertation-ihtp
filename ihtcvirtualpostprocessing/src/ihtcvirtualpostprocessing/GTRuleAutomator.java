package ihtcvirtualpostprocessing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;

public class GTRuleAutomator {
	
	private EPackage metamodel;
	private StringBuilder gtContent;
	
	static class VirtualNodeInfo {
		String className;
        String sourceClass;
        String targetClass;
        String sourceRef;
        String targetRef;
        String sourceEdgeReference;
        String targetEdgeReference;
        
        VirtualNodeInfo(String name, String sClass, String tClass, String sRef, String tRef, String sEdge, String tEdge) {
        	this.className = name;
            this.sourceClass = sClass;
            this.targetClass = tClass;
            this.sourceRef = sRef;
            this.targetRef = tRef;
            this.sourceEdgeReference = sEdge;
            this.targetEdgeReference = tEdge;
        }
        
        public String getName() {
        	return this.className;
        }
    }

	/**
	 * Constructor - loads the metamodel
	 */
	public GTRuleAutomator() throws IOException {
		this.metamodel = loadEcoreMetamodel();
		this.gtContent = new StringBuilder();
		
		System.out.println("Metamodel loaded: " + metamodel.getName());
		System.out.println("Number of classes: " + metamodel.getEClassifiers().size());
	}

    private EPackage loadEcoreMetamodel() throws IOException {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry()
            .getExtensionToFactoryMap()
            .put("ecore", new EcoreResourceFactoryImpl());
        
        String ecorePath = "../ihtcvirtualmetamodel/model/Ihtcvirtualmetamodel.ecore";
        File ecoreFile = new File(ecorePath);
        
        if (!ecoreFile.exists()) {
            throw new IOException("File not found: " + ecoreFile.getAbsolutePath());
        }
        
        URI uri = URI.createFileURI(ecoreFile.getAbsolutePath());
        Resource resource = resourceSet.getResource(uri, true);
        
        return (EPackage) resource.getContents().get(0);
    }
    
    /**
     * Generate all rules from virtual node annotations
     */
    public void generateRules() {
    	gtContent.setLength(0); 
        gtContent.append("import \"platform:/resource/ihtcvirtualmetamodel/model/Ihtcvirtualmetamodel.ecore\"\n\n");
        
        for (EClassifier classifier : metamodel.getEClassifiers()) {
            if (classifier instanceof EClass) {
                EClass eClass = (EClass) classifier;
                
                VirtualNodeInfo info = getVirtualNodeInfo(eClass);
                
                if (info != null) {
                    generateConversionRule(info);
                    generateRemovalRule(info);
                    gtContent.append("\n");
                }
            }
        }
    }
    
    private VirtualNodeInfo getVirtualNodeInfo(EClass eClass) {
        for (EAnnotation annotation : eClass.getEAnnotations()) {
            if ("virtualNode".equals(annotation.getSource())) {
                String sourceClass = annotation.getDetails().get("sourceClass");
                String targetClass = annotation.getDetails().get("targetClass");
                String sourceRef = annotation.getDetails().get("sourceReference");
                String targetRef = annotation.getDetails().get("targetReference");
                String sourceEdge = annotation.getDetails().get("sourceEdgeReference");
                String targetEdge = annotation.getDetails().get("targetEdgeReference");
                
                return new VirtualNodeInfo(
                    eClass.getName(),
                    sourceClass,
                    targetClass,
                    sourceRef,
                    targetRef,
                    sourceEdge,
                    targetEdge
                );
            }
        }
        return null;
    }
    
    /**
     * Generate the conversion rule (e.g., virtualShiftToWorkload_to_derived)
     */
    private void generateConversionRule(VirtualNodeInfo info) {
        // Convert to camelCase
        String camelCaseClass = info.className.substring(0, 1).toLowerCase() + 
                                info.className.substring(1);
        String ruleName = camelCaseClass + "_to_derived";
        
        String vVar = getVariableName(info.className);
        String sourceVar = getVariableName(info.sourceClass);
        String targetVar = getVariableName(info.targetClass);
        
        // Virtual references use opposite class name
        String sourceVirtualRef = "virtual" + info.targetClass;
        String targetVirtualRef = "virtual" + info.sourceClass;
        
        gtContent.append(String.format("""
            //
            // Remove objects of the type `%s`
            //
            rule %s {
            \t-- %s : %s {
            \t\t---%s -> %s
            \t\t---%s -> %s
            \t}

            \t%s : %s {
            \t\t++ -%s -> %s
            \t\t-- -%s -> %s
            \t}

            \t%s : %s {
            \t\t++ -%s -> %s
            \t\t-- -%s -> %s
            \t}
            \t
            \t# %s.isSelected == true
            }
            """,
            info.className,
            ruleName,
            vVar, info.className,
            info.sourceRef, sourceVar,
            info.targetRef, targetVar,
            sourceVar, info.sourceClass,
            info.sourceEdgeReference, targetVar,
            sourceVirtualRef, vVar,
            targetVar, info.targetClass,
            info.targetEdgeReference, sourceVar,
            targetVirtualRef, vVar,
            vVar
        ));
    }
    
    /**
     * Generate the removal rule (e.g., removeVirtualShiftToWorkload)
     */
    private void generateRemovalRule(VirtualNodeInfo info) {
        String ruleName = "remove" + info.className;
        String vVar = getVariableName(info.className);
        
        gtContent.append(String.format("""
            rule %s {
            \t-- %s : %s
            \t# %s.isSelected == false
            }
            """,
            ruleName,
            vVar, info.className,
            vVar
        ));
    }
    
    /**
     * Get a short variable name (e.g., "Shift" -> "s", "Workload" -> "w")
     */
    private String getVariableName(String className) {
        return className.substring(0, 1).toLowerCase();
    }
    
    /**
     * Write the generated GT rules to a file
     */
    public void writeToFile(String outputPath) throws IOException {
        java.nio.file.Path path = Paths.get(outputPath);
        
        // Only create directories if there's a parent directory
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        
        // Write the file (creates if doesn't exist, overwrites if does)
        Files.write(path, gtContent.toString().getBytes());
        System.out.println("\nGenerated at: " + outputPath);
    }
    
    /**
     * Get the generated GT content as string
     */
    public String getGTContent() {
        return gtContent.toString();
    }
    
    /**
     * Main method - creates instance and runs automator
     */
    public static void main(String[] args) throws IOException {
        GTRuleAutomator automator = new GTRuleAutomator();
        
        automator.generateRules();
        automator.writeToFile("src/ihtcvirtualpostprocessing/PostProcessingGTRules.gt");
        
        System.out.println("GT Rules generated successfully");
    }
}