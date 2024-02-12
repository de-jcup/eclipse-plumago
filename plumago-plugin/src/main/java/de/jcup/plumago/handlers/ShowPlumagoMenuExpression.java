/* SPDX-License-Identifier: Apache-2.0 */
package de.jcup.plumago.handlers;

import org.eclipse.e4.core.di.annotations.Evaluate;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.eclipse.e4.ui.workbench.modeling.ESelectionService;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.ITreeSelection;

import jakarta.inject.Inject;

public class ShowPlumagoMenuExpression {

    @Inject
    EPartService partService;

    @Inject
    ESelectionService selectionService;

    private boolean debug=Boolean.getBoolean("de.jcup.debug");
    
    @Evaluate
    public boolean evaluate(EModelService modelService) {

	Object selection = selectionService.getSelection();
	if (selection == null) {
	    return false;
	}
	if (selection instanceof ITreeSelection) {
	    ITreeSelection treeSelection = (ITreeSelection) selection;
	    Object firstElement = treeSelection.getFirstElement();
	    if (firstElement == null) {
		return false;
	    }
	    
	    if (firstElement instanceof ICompilationUnit) {
		ICompilationUnit unit = (ICompilationUnit) firstElement;
		if (debug) {
		    System.out.println("x-firstElement.comilationunit = " + firstElement.getClass() + " - " + firstElement);
		}
		IType[] types;
		try {
		    
		    types = unit.getTypes();
		    
		    if (debug) {
			StringBuilder sb = new StringBuilder();
			for (IType type: types) {
			    sb.append(type.getTypeQualifiedName());
			    sb.append(":");
			    sb.append(type.getElementName());
			    sb.append("-");
			    sb.append(type.getClass());
			    sb.append(",");
			}
			System.out.println("- compilation unit found. types = " + sb.toString());
		    }
		    if (types.length>0) {
			return true;
		    }
		} catch (JavaModelException e) {
		    e.printStackTrace();
		}
	    }
	}
	return false;
    }

}
