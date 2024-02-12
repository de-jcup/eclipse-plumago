/* SPDX-License-Identifier: Apache-2.0 */
package de.jcup.plumago.generation;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;

public class PlantUMLGeneratorResultData {

    IType type;
    
    String plantUMLString;

    ICompilationUnit compilationUnit;
    
    public IType getType() {
	return type;
    }
    
    public String getPlantUMLString() {
	return plantUMLString;
    }
    
    public ICompilationUnit getCompilationUnit() {
	return compilationUnit;
    }
}
