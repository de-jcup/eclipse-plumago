package de.jcup.plumago.generation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jface.viewers.IStructuredSelection;

import jakarta.inject.Singleton;

@Creatable
@Singleton
public class JDTHelper {

    private ASTParser parser;

    public JDTHelper() {
	parser = ASTParser.newParser(AST.getJLSLatest());
	parser.setResolveBindings(true);
    }

    public ICompilationUnit getCompilationUnit(IStructuredSelection structuredSelection) {
	if (structuredSelection == null) {
	    return null;
	}

	Object firstElement = structuredSelection.getFirstElement();
	if (!(firstElement instanceof ICompilationUnit)) {
	    return null;
	}
	ICompilationUnit compilationUnit = (ICompilationUnit) firstElement;
	return compilationUnit;
    }

    public InspectionContext generate(IType type, IProgressMonitor monitor) throws JavaModelException {
	StringBuilder sb = new StringBuilder();
	InspectionContext context = new InspectionContext(sb);
	if (type == null) {
	    return context;
	}

	ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
	parser.setResolveBindings(true);
	parser.setSource(type.getCompilationUnit());
	monitor.beginTask("Start parsing AST and inspection", IProgressMonitor.UNKNOWN);
	ASTNode unitNode = parser.createAST(monitor);

	unitNode.accept(new ASTInspector(type, context));

	int maximumLoops = 10;
	int loop = 0;
	while (context.needsIntrospection() && loop < maximumLoops) {
	    loop++;
	    Set<IType> formerToInspect = new HashSet<>(context.getToInspect());
	    for (IType typeToInspect : formerToInspect) {

		if (context.isAlreadyInspected(typeToInspect)) {
		    continue;
		}
		ICompilationUnit compilationUnit = typeToInspect.getCompilationUnit();
		if (compilationUnit != null) {
		    parser.setResolveBindings(true);
		    parser.setSource(compilationUnit);
		    ASTNode unitNode2 = parser.createAST(monitor);
		    ASTInspector visitor = new ASTInspector(typeToInspect, context);
		    unitNode2.accept(visitor);
		}
	    }
	}
	monitor.done();
	return context;
    }

    private class ElementInfo {
	private boolean isMap;
	private boolean isCollection;
	public String fieldTypeResolved;
	public boolean primitive;
	public boolean isStatic;
    }

    private class ASTInspector extends ASTVisitor {
	IField[] fields;
	private InspectionContext context;
	private IType type;
	private ClassData classData;

	public ASTInspector(IType type, InspectionContext context) throws JavaModelException {
	    this.type = type;
	    fields = type.getFields();
	    this.context = context;
	    classData = new ClassData();
	    classData.fullClassName = type.getFullyQualifiedName();
	    context.classes.add(classData);
	}

	@Override
	public boolean visit(MethodDeclaration declaration) {
	    String methodName = declaration.getName().getIdentifier();
	    String ownerFullName = declaration.resolveBinding().getDeclaringClass().getBinaryName();

	    Type returnType = declaration.getReturnType2();

	    @SuppressWarnings("unchecked")
	    List<SingleVariableDeclaration> params = declaration.parameters();
	    boolean isCreatingReturnType = !methodName.startsWith("get") && returnType != null;
	    if (isCreatingReturnType) {
		for (SingleVariableDeclaration variableDeclaration : params) {
		    if (returnType.equals(variableDeclaration.getType())) {
			isCreatingReturnType = false;/// the parameter is same type, so either so normlly not a
						     /// "creation" method
		    }
		}

	    }
	    if (returnType != null) {
		try {
		    ElementInfo info = context.createElementInfo(returnType);
		    if (isCreatingReturnType) {
			markAsCreates(ownerFullName, info);
		    } else {
			markAsUses(ownerFullName, info);
		    }
		} catch (JavaModelException e) {
		    e.printStackTrace();
		}
	    }

	    /* params */
	    for (SingleVariableDeclaration variableDeclaration : params) {
		Type varType = variableDeclaration.getType();
		try {
		    ElementInfo info = context.createElementInfo(varType);
		    markAsUses(ownerFullName, info);
		} catch (JavaModelException e) {
		    e.printStackTrace();
		}

	    }
	    return true;
	}

	@Override
	public boolean visit(VariableDeclarationFragment fragment) {
	    try {
		inspect(fragment);
	    } catch (JavaModelException e) {
		e.printStackTrace();
	    }
	    return true;
	}

	private void inspect(VariableDeclarationFragment fragment) throws JavaModelException {
	    IVariableBinding resolvedBinding = fragment.resolveBinding();
	    if (resolvedBinding == null) {
		return;
	    }
	    context.markAsInspected(type);
	    IJavaElement element = resolvedBinding.getJavaElement();
	    for (int i = 0; i < fields.length; ++i) {

		IField field = fields[i];
		if (!field.equals(element)) {
		    continue;
		}
		FieldDeclaration fieldDeclaration = (FieldDeclaration) fragment.getParent();
		Type fieldDomType = fieldDeclaration.getType();
		int modifiers = fieldDeclaration.getModifiers();
		Visibility visibility = null;
		if (Modifier.isPrivate(modifiers)) {
		    visibility = Visibility.PRIVATE;
		} else if (Modifier.isPublic(modifiers)) {
		    visibility = Visibility.PRIVATE;
		} else if (Modifier.isProtected(modifiers)) {
		    visibility = Visibility.PROTECTED;
		} else {
		    visibility = Visibility.PACKAGE_PRIVATE;
		}
		ElementInfo info = context.createElementInfo(fieldDomType);
		if (Modifier.isStatic(modifiers)) {
		    info.isStatic = true;
		}

		markAsContains(visibility, field, info);
	    }
	}

	private void markAsCreates(String ownerFullName, ElementInfo info) {
	    classData.addReference(info, ReferenceData.CREATES);
	}

	private void markAsUses(String ownerFullName, ElementInfo info) {
	    classData.addReference(info, ReferenceData.USES);
	}

	private void markAsContains(Visibility visibility, IField field, ElementInfo info) {

	    classData.addField(visibility, info, field.getElementName());
	    classData.addReference(info, ReferenceData.CONTAINS);
	}

    }

    // see https://plantuml.com/class-diagram#3644720244dd6c6a (defining visibility)
    public enum Visibility {
	PRIVATE('-'), PACKAGE_PRIVATE('~'), PROTECTED('#'), PUBLIC('+'),;

	char c;

	private Visibility(char c) {
	    this.c = c;
	}

	public char getPlantUMLCharacter() {
	    return c;
	}
    }

    public enum Cardinality {
	ONE, MANY,
    }

    public class Connection {
	IType declaringType;
	IField field;
	IType fieldType;
	String connectionName;
	Cardinality cardinality;

    }

    public class FieldData {
	public Visibility visibility;
	public String fieldTypeResolved;
	public String elementName;
	public boolean isStatic;
    }

    public enum ReferenceData {
	CONTAINS,

	USES, CREATES,

	MULTIPLE_REFERNECES,

    }

    private boolean referenceToItselfIsShown = false;

    public class ClassData {

	private ClassData() {

	}

	private Map<String, ReferenceData> referenceMap = new TreeMap<>();

	public void addReference(ElementInfo info, ReferenceData referenceData) {
	    if (info.primitive || info.fieldTypeResolved == null || info.fieldTypeResolved.indexOf('.') == -1) {
		return;
	    }
	    String otherClassName = info.fieldTypeResolved;
	    if (!referenceToItselfIsShown && otherClassName.equals(fullClassName)) {
		return;
	    }
	    ReferenceData ref = referenceMap.get(otherClassName);
	    if (ref == null) {
		referenceMap.put(otherClassName, referenceData);
	    } else {
		referenceMap.put(otherClassName, ReferenceData.MULTIPLE_REFERNECES);
	    }
	}

	public String fullClassName;

	public List<FieldData> fields = new ArrayList<>();

	public void addField(Visibility visibility, ElementInfo info, String elementName) {
	    if (info.primitive || info.fieldTypeResolved == null || info.fieldTypeResolved.indexOf('.') == -1) {
		return;
	    }
	    FieldData fieldData = new FieldData();
	    fieldData.visibility = visibility;
	    fieldData.isStatic = info.isStatic;
	    fieldData.fieldTypeResolved = info.fieldTypeResolved;
	    fieldData.elementName = elementName;
	    fields.add(fieldData);
	}

	public Map<String, ReferenceData> getReferenceMap() {
	    return referenceMap;
	}

    }

    private Map<String, String> mapSimpleNames = new LinkedHashMap<>();

    public String xresolveReferenceName(String className) {
	String ref = mapSimpleNames.get(className);
	if (ref == null) {
	    String simpleClassName = createSimpleClassName(className);
	    for (int i = 0; i < 255; i++) {
		ref = simpleClassName.toLowerCase() + "_" + i;
		if (!mapSimpleNames.values().contains(ref)) {
		    mapSimpleNames.put(className, ref);
		    break;
		}
	    }
	}
	return ref;
    }

    public String createPackageName(String fullClassName) {
	int lastDotIndex = fullClassName.lastIndexOf('.');
	String simpleName = fullClassName.substring(0, lastDotIndex);
	return simpleName;
    }

    public String createSimpleClassName(String fullClassName) {
	int lastDotIndex = fullClassName.lastIndexOf('.');
	String simpleName = fullClassName.substring(lastDotIndex + 1);
	return simpleName;
    }

    public class InspectionContext {
	private StringBuilder relationShipStrings = new StringBuilder();
	private Set<IType> alreadyInspected = new HashSet<>();
	private Set<IType> toInspect = new HashSet<>();

	private List<ClassData> classes = new ArrayList<>();

	public StringBuilder getRelationShipStrings() {
	    return relationShipStrings;
	}

	public Map<String, List<ClassData>> getPackages() {
	    Map<String, List<ClassData>> packageMap = new TreeMap<>();

	    List<ClassData> classDataList = getAllClasses();
	    for (ClassData classData : classDataList) {
		String packageName = createPackageName(classData.fullClassName);
		List<ClassData> packageClassDataList = packageMap.get(packageName);
		if (packageClassDataList == null) {
		    packageClassDataList = new ArrayList<>();
		    packageMap.put(packageName, packageClassDataList);
		}
		packageClassDataList.add(classData);
	    }

	    return packageMap;
	}

	public List<ClassData> getAllClasses() {
	    return classes;
	}

	public InspectionContext(StringBuilder sb) {
	    this.relationShipStrings = sb;
	}

	public boolean needsIntrospection() {
	    return toInspect.size() > 0;
	}

	public void markToInspect(IType type) {
	    if (alreadyInspected.contains(type)) {
		return;
	    }
	    toInspect.add(type);
	}

	public void markAsInspected(IType type) {
	    alreadyInspected.add(type);
	    toInspect.remove(type);
	}

	public boolean isAlreadyInspected(IType type) {
	    return alreadyInspected.contains(type);
	}

	public Set<IType> getToInspect() {
	    return toInspect;
	}

	private ElementInfo createElementInfo(Type type) throws JavaModelException {
	    ITypeBinding typeBinding = type.resolveBinding();
	    IJavaElement javaElement = typeBinding.getJavaElement();

	    ElementInfo info = new ElementInfo();
	    info.isCollection = false;
	    info.isMap = false;
	    if (javaElement instanceof IType) {
		IType javaType = (IType) javaElement;

		String[] superInterfaceNames = javaType.getSuperInterfaceNames();
		for (String superInterfacename : superInterfaceNames) {
		    info.isCollection = "java.util.Collection".equals(superInterfacename);
		    if (info.isCollection) {
			break;
		    }
		    info.isMap = "java.util.Map".equals(superInterfacename);
		    if (info.isMap) {
			break;
		    }

		}
		markToInspect(javaType);
	    }

	    String fieldTypeResolved = type.resolveBinding().getQualifiedName();
	    if (type instanceof ParameterizedType) {
		ParameterizedType paramType = (ParameterizedType) type;
		Type fieldTypeWithoutParams = paramType.getType();

		ITypeBinding fieldTypeBindingWithoutParams = fieldTypeWithoutParams.resolveBinding();
		fieldTypeResolved = fieldTypeBindingWithoutParams.getBinaryName();
		if (info.isCollection) {
		    List<?> args = paramType.typeArguments();
		    for (Object arg : args) {
			if (arg instanceof Type) {
			    Type t = (Type) arg;
			    ITypeBinding resolvedBinding = t.resolveBinding();
			    IJavaElement javaElement2 = resolvedBinding.getJavaElement();
			    if (javaElement2 instanceof IType) {
				markToInspect((IType) javaElement2);
			    }
			    fieldTypeResolved = resolvedBinding.getQualifiedName();

			}
		    }
		}
	    }
	    info.fieldTypeResolved = fieldTypeResolved;
	    info.primitive = fieldTypeResolved.indexOf('.') == -1;

	    return info;
	}
    }

}
