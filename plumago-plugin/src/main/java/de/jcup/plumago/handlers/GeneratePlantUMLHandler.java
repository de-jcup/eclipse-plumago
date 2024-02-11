package de.jcup.plumago.handlers;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.core.services.log.Logger;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.swt.widgets.Shell;

import de.jcup.plumago.generation.PlantUMLGenerator;
import de.jcup.plumago.generation.PlantUMLGeneratorResultData;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * <b>Warning</b> : As explained in <a href=
 * "http://wiki.eclipse.org/Eclipse4/RCP/FAQ#Why_aren.27t_my_handler_fields_being_re-injected.3F">this
 * wiki page</a>, it is not recommended to define @Inject fields in a handler.
 * <br/>
 * <br/>
 * <b>Inject the values in the @Execute methods</b>
 */
public class GeneratePlantUMLHandler {

    @Inject
    IEclipseContext context;

    @Inject
    PlantUMLGenerator generator; // is okay - no re-injection necessary

    @Execute
    public void execute(@Named(IServiceConstants.ACTIVE_SHELL) Shell s) {

	Logger logger = context.get(Logger.class);
	logger.info("Plantuml generation starting");

	List<PlantUMLGeneratorResultData> result = generator.generateFromSelection();

	for (PlantUMLGeneratorResultData data : result) {
	    ICompilationUnit compilationUnit = data.getCompilationUnit();

	    String fileName = "";
	    IType type = data.getType();
	    if (type != null) {
		fileName += type.getElementName();
	    } else {
		fileName += "unknown_" + System.currentTimeMillis();
	    }
	    fileName += "_plumego.puml";

	    String parentFolderPath = null;
	    IContainer parent = null;
	    IResource resource = compilationUnit.getResource();
	    if (resource.getType() == IResource.FILE) {

		IFile ifile = (IFile) resource;
		parent = ifile.getParent();
		parentFolderPath = parent.getRawLocation().toString();

	    } else {
		Path path;
		try {
		    path = Files.createTempDirectory("plumego");
		} catch (IOException e) {
		    logger.error("Was not able to create temp plumego directory", e);
		    continue;
		}
		parentFolderPath = path.toString();
	    }
	    Path targetPath = Paths.get(parentFolderPath, fileName);

	    try {
		Files.deleteIfExists(targetPath);
		Files.writeString(targetPath, data.getPlantUMLString(), Charset.forName("UTF-8"));
	    } catch (IOException e) {
		logger.error("Was not able to write plantuml to file", e);
	    }

	    if (parent != null) {
		try {
		    parent.refreshLocal(IResource.DEPTH_ONE, null);
		} catch (CoreException e) {
		    logger.error("Was not able to write plantuml to file", e);
		}
	    }
	}

    }

}
