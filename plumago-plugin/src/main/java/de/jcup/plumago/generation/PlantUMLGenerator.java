package de.jcup.plumago.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.e4.ui.workbench.modeling.ESelectionService;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.IStructuredSelection;

import de.jcup.plumago.generation.JDTHelper.ClassData;
import de.jcup.plumago.generation.JDTHelper.FieldData;
import de.jcup.plumago.generation.JDTHelper.InspectionContext;
import de.jcup.plumago.generation.JDTHelper.ReferenceData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;


@Creatable
@Singleton
public class PlantUMLGenerator {
    
    @Inject
    ESelectionService selectionService;

    @Inject
    private JDTHelper helper;

    public List<PlantUMLGeneratorResultData> generateFromSelection() {
	List<PlantUMLGeneratorResultData> plantUMLDataList= new ArrayList<>();
	
	Object selection = selectionService.getSelection();
	if (!(selection instanceof IStructuredSelection)) {
	    return plantUMLDataList;
	}
	return generateFromStructuredSelection(plantUMLDataList, (IStructuredSelection) selection);
    }

    private List<PlantUMLGeneratorResultData> generateFromStructuredSelection(List<PlantUMLGeneratorResultData> plantUMLDataList,
	    IStructuredSelection structuredSelection) {
	ICompilationUnit compilationUnit = getHelper().getCompilationUnit(structuredSelection);

	try {
	    IType[] allTypes = compilationUnit.getAllTypes();
	    for (IType type : allTypes) {
		
		PlantUMLGeneratorResultData data = new PlantUMLGeneratorResultData();
		data.type=type;
		data.plantUMLString=generatePlantUMLFor(type);
		data.compilationUnit = compilationUnit;
		plantUMLDataList.add(data);
	    }

	} catch (JavaModelException e) {
	    e.printStackTrace();
	}
	return plantUMLDataList;
    }

    private String generatePlantUMLFor(IType type) throws JavaModelException {
	
	InspectionContext context = getHelper().generate(type, new NullProgressMonitor());

	String fullName = type.getFullyQualifiedName();
	int lastDotIndex = fullName.lastIndexOf('.');
	String inspectedTypePackageName = null;
	if (lastDotIndex > 0) {
	    inspectedTypePackageName = fullName.substring(0, lastDotIndex - 1);
	}

	/* class definition */
	StringBuilder classesString = new StringBuilder();
	Map<String, List<ClassData>> packages = context.getPackages();
	for (String currentPackageName : packages.keySet()) {
	    if (!currentPackageName.startsWith(inspectedTypePackageName)) {
		continue;
	    }
	    classesString.append("package ").append(currentPackageName).append("{\n");
	    List<ClassData> classes = packages.get(currentPackageName);
	    for (ClassData data : classes) {
		classesString.append("'").append(data.fullClassName).append("\n");
		classesString.append("   class ").append(getHelper().createSimpleClassName(data.fullClassName));
		if (data.fullClassName.equals(fullName)) {
		    classesString.append(" ##[bold]black ");
		}
		classesString.append("{\n");
		for (FieldData fieldData : data.fields) {
		    classesString.append("    ");
		    if (fieldData.isStatic) {
			classesString.append("{static} ");
		    }
		    classesString.append(fieldData.visibility.getPlantUMLCharacter());
		    classesString.append(getHelper().createSimpleClassName(fieldData.fieldTypeResolved)).append(" : ")
			    .append(fieldData.elementName).append("\n");
		}
		classesString.append("  }\n");

		Map<String, ReferenceData> referenceMap = data.getReferenceMap();
		String simpleClassName = getHelper().createSimpleClassName(data.fullClassName);
		for (String classNameReferenced : referenceMap.keySet()) {
		    String otherClassName = classNameReferenced;
		    String otherPackageName = getHelper().createPackageName(classNameReferenced);
		    if (otherPackageName.startsWith("java.")) {
			// we do not show references to java runtime
			continue;
		    }
		    if (otherPackageName.equals(currentPackageName)) {
			otherClassName = getHelper().createSimpleClassName(classNameReferenced);
		    }
		    ReferenceData referenceType = referenceMap.get(classNameReferenced);
		    classesString.append("  ").append(simpleClassName);
		    String refString = "..";
		    switch (referenceType) {
		    case CONTAINS:
			refString = "*--";
			break;
		    case CREATES:
			refString = "..";
			break;
		    case MULTIPLE_REFERNECES:
			refString = "-[#blue]-";
			break;
		    case USES:
			refString = ".[#green].";
			break;
		    default:
			break;

		    }
		    classesString.append(" ").append(refString).append(" ").append(otherClassName).append("\n");
		}

	    }
	    classesString.append("}\n\n");
	}

	StringBuilder sb2 = new StringBuilder();
	sb2.append("@startuml\n");
	sb2.append("title \"Inspected class:").append(fullName).append("\"\n");// title is inspected class
	sb2.append("skinparam linetype ortho\n");// we want straight lines, easier to read
	sb2.append("hide empty methods\n");
	sb2.append("hide empty fields\n");
	sb2.append(classesString);
	sb2.append("@enduml");
	String plantuml = sb2.toString();
	return plantuml;

    }

    private JDTHelper getHelper() {
	return helper;
    }

}
