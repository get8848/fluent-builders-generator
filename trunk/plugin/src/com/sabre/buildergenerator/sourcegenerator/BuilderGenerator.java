/**
 * Copyright (c) 2009-2010 fluent-builder-generator for Eclipse commiters.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sabre Polska sp. z o.o. - initial implementation during Hackday
 */

package com.sabre.buildergenerator.sourcegenerator;

import com.sabre.buildergenerator.Activator;
import com.sabre.buildergenerator.signatureutils.SignatureParserException;
import com.sabre.buildergenerator.signatureutils.SignatureResolver;
import com.sabre.buildergenerator.sourcegenerator.TypeHelper.MethodInspector;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;

import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class BuilderGenerator {
    static final String SETTER_PREFIX = "set";

    private final Set<String> typesAlradyGeneratedInnerBuilders = new HashSet<String>();
    private final Set<String> typesToGenerateInnerBuilders = new HashSet<String>();
    private final Map<String, Set<String>> typesAndFieldsToGenerate = new HashMap<String, Set<String>>();

    public String generateSource(final IType type, String packageName, String builderName, Set<IMethod> selectedSetters,
        String setterPrefix, String collectionSetterPrefix, String endPrefix, boolean doFormat) throws Exception {

        if (selectedSetters != null) {
            for (IMethod method : selectedSetters) {
                IType declaringType = method.getDeclaringType();
                String typeName = declaringType.getFullyQualifiedName();
                String typeSignature = Signature.createTypeSignature(typeName, true);
                String fieldName = fieldNameFromSetter(method);
                Set<String> fieldNames = typesAndFieldsToGenerate.get(typeSignature);
                if (fieldNames == null) {
                    fieldNames = new HashSet<String>();
                    typesAndFieldsToGenerate.put(typeSignature, fieldNames);
                }
                fieldNames.add(fieldName);
            }
        }
        final AbstractBuilderSourceGenerator<String> generator = new BuilderSourceGenerator();

        generator.setSetterPrefix(setterPrefix);
        generator.setCollectionElementSetterPrefix(collectionSetterPrefix);
        generator.setEndPrefix(endPrefix);

        StringWriter sw = new StringWriter();

        generator.setOut(new PrintWriter(sw));

        ITypeParameter[] typeParameters = type.getTypeParameters();
        String[] typeParamNames = new String[typeParameters.length];
        int i = 0;
        for (ITypeParameter typeParameter : typeParameters) {
            typeParamNames[i++] = typeParameter.getElementName();
        }
        generator.addBuilderClass(type.getFullyQualifiedName(), packageName, builderName, typeParamNames);
        typesToGenerateInnerBuilders.add(Signature.createTypeSignature(type.getFullyQualifiedName(), true));

        generateBuilderBaseClasses(generator, type);
        generator.finish();
        sw.flush();

        String builderSource = sw.toString();

        if (doFormat) {
            builderSource = formatSource(builderSource);
        }

        return builderSource;
    }

    private String formatSource(String builderSource) {
        TextEdit text = ToolFactory.createCodeFormatter(null).format(CodeFormatter.K_COMPILATION_UNIT, builderSource, 0,
                builderSource.length(), 0, "\n");

        // text is null if source cannot be formatted
        if (text != null) {
            Document simpleDocument = new Document(builderSource);

            try {
                text.apply(simpleDocument);
            } catch (MalformedTreeException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (BadLocationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                builderSource = simpleDocument.get();
            }
        }

        return builderSource;
    }

    private void addTypeToGenerateInnerBuilder(String elementTypeSignature) {
        if ((typesAndFieldsToGenerate == null || typesAndFieldsToGenerate.get(elementTypeSignature) != null)
                && !typesAlradyGeneratedInnerBuilders.contains(elementTypeSignature)) {
            typesToGenerateInnerBuilders.add(elementTypeSignature);
        }
    }

    private void generateBuilderBaseClasses(final AbstractBuilderSourceGenerator<String> generator, final IType enclosingType)
        throws Exception {
        while (!typesToGenerateInnerBuilders.isEmpty()) {
            String typeSignature = typesToGenerateInnerBuilders.iterator().next();
            String type = SignatureResolver.signatureToTypeName(SignatureResolver.resolveSignature(enclosingType,
                        typeSignature));

            final IType resolvedType = SignatureResolver.resolveType(enclosingType, typeSignature);

            generator.startBuilderBaseClass(type, resolvedType); // following methods might add elements to typesUsed

            final Set<String> fieldNamesSet = null; //fieldNames != null && fieldNames.length > 0 ? new HashSet<String>(Arrays.asList(fieldNames)) : null;

            TypeHelper.findSetterMethods(resolvedType, new MethodInspector() {
                    public void nextMethod(IType methodOwnerType, IMethod method,
                        Map<String, String> parameterSubstitution) throws Exception {
                        Activator.getDefault().getLog().log(new Status(IStatus.OK, Activator.PLUGIN_ID, "nextMethod method=" + method.getElementName() + " type=" + methodOwnerType.getElementName()));

                        String fieldName = fieldNameFromSetter(method);

                        if (fieldNamesSet == null || fieldNamesSet.contains(fieldName)) {
                            try {
                                String parameterTypeSignature = method.getParameterTypes()[0];
                                String qualifiedParameterTypeSignature = SignatureResolver.resolveTypeWithParameterMapping(
                                        methodOwnerType, parameterTypeSignature, parameterSubstitution);

                                String[] exceptionTypes;

                                exceptionTypes = method.getExceptionTypes();

                                for (int i = 0; i < exceptionTypes.length; i++) {
                                    exceptionTypes[i] = SignatureResolver.resolveTypeWithParameterMapping(methodOwnerType,
                                        exceptionTypes[i], parameterSubstitution);
                                    exceptionTypes[i] = SignatureResolver.signatureToTypeName(exceptionTypes[i]);
                                }

                                generateSimpleSetter(generator, methodOwnerType, exceptionTypes, fieldName, qualifiedParameterTypeSignature);
                                generateCollectionAdder(generator, methodOwnerType, exceptionTypes, fieldName, qualifiedParameterTypeSignature);
                                generateCollectionBuilder(generator, methodOwnerType, exceptionTypes, fieldName, qualifiedParameterTypeSignature);
                                generateFieldBuilder(generator, methodOwnerType, exceptionTypes, fieldName, qualifiedParameterTypeSignature);
                            } catch (JavaModelException e) {
                            }
                        }
                    }
                });

            generator.endBuilderBaseClass();
            typesToGenerateInnerBuilders.remove(typeSignature);
            typesAlradyGeneratedInnerBuilders.add(typeSignature);
        }
    }

    private void generateSimpleSetter(AbstractBuilderSourceGenerator<String> generator, IType enclosingType, String[] exceptionTypes, String fieldName,
        String fieldTypeSignature) {
        String fieldType = SignatureResolver.signatureToTypeName(fieldTypeSignature);
        generator.addFieldSetter(fieldName, fieldType, exceptionTypes);
    }

    private void generateCollectionAdder(AbstractBuilderSourceGenerator<String> generator, IType enclosingType, String[] exceptionTypes,
        String fieldName, String resolvedFieldTypeSignature) throws Exception {
        boolean isFieldACollection = TypeHelper.isCollection(enclosingType, resolvedFieldTypeSignature);
        if (isFieldACollection) {
            String fieldType = SignatureResolver.signatureToTypeName(resolvedFieldTypeSignature);
            String elementTypeSignature = TypeHelper.getTypeParameterSignature(resolvedFieldTypeSignature);
            String elementType = SignatureResolver.signatureToTypeName(elementTypeSignature);
            String elementName = pluralToSingle(fieldName);

            String fieldTypeErasureSignature = Signature.getTypeErasure(resolvedFieldTypeSignature);
            String concreteCollectionType =  abstractToConcreteCollectionType(fieldTypeErasureSignature);

            generator.addCollectionElementSetter(fieldName, fieldType, elementName, elementType, concreteCollectionType, exceptionTypes);
        }
    }

    private void generateCollectionBuilder(AbstractBuilderSourceGenerator<String> generator, IType enclosingType,
        String[] exceptionTypes, String fieldName, String resolvedFieldTypeSignature) throws Exception {
        boolean isFieldACollection = TypeHelper.isCollection(enclosingType, resolvedFieldTypeSignature);
        if (isFieldACollection) {
            String fieldType = SignatureResolver.signatureToTypeName(resolvedFieldTypeSignature);
            String elementTypeSignature = TypeHelper.getTypeParameterSignature(resolvedFieldTypeSignature);
            String elementType = SignatureResolver.signatureToTypeName(elementTypeSignature);
            String elementName = pluralToSingle(fieldName);

            String[] typeParams = Signature.getTypeArguments(elementTypeSignature);

            for (int i = 0; i < typeParams.length; i++) {
                String sig = SignatureResolver.resolveSignature(enclosingType, typeParams[i]);
                typeParams[i] = SignatureResolver.signatureToTypeName(sig);
            }

            if (isSourceClass(enclosingType, elementTypeSignature)) {
                generator.addCollectionElementBuilder(fieldName, fieldType, elementName, elementType, exceptionTypes, typeParams);
                addTypeToGenerateInnerBuilder(Signature.getTypeErasure(elementTypeSignature));
            }
        }
    }

    private void generateFieldBuilder(AbstractBuilderSourceGenerator<String> generator, IType enclosingType,
        String[] exceptionTypes, String fieldName, String resolvedFieldTypeSignature)
        throws Exception {
        String fieldType = SignatureResolver.signatureToTypeName(resolvedFieldTypeSignature);

        String[] typeParams = Signature.getTypeArguments(resolvedFieldTypeSignature);
        for (int i = 0; i < typeParams.length; i++) {
            String sig = SignatureResolver.resolveSignature(enclosingType, typeParams[i]);
            typeParams[i] = SignatureResolver.signatureToTypeName(sig);
        }

        if (isSourceClass(enclosingType, resolvedFieldTypeSignature)) {
            generator.addFieldBuilder(fieldName, fieldType, exceptionTypes, typeParams);
            addTypeToGenerateInnerBuilder(Signature.getTypeErasure(resolvedFieldTypeSignature));
        }
    }

    private String abstractToConcreteCollectionType(String collectionTypeErasureSignature) {
        String concreteCollectionType;
        if (collectionTypeErasureSignature.equals("Qjava.util.Collection;")) {
            concreteCollectionType = "java.util.ArrayList";
        } else if (collectionTypeErasureSignature.equals("Qjava.util.List;")) {
            concreteCollectionType = "java.util.ArrayList";
        } else if (collectionTypeErasureSignature.equals("Qjava.util.Set;")) {
            concreteCollectionType = "java.util.HashSet";
        } else {
            concreteCollectionType = SignatureResolver.signatureToTypeName(collectionTypeErasureSignature);
        }
        return concreteCollectionType;
    }

    private boolean isSourceClass(IType enclosingType, String typeSignature) throws JavaModelException, SignatureParserException {
        IType type = SignatureResolver.resolveType(enclosingType, typeSignature);
        return type != null && type.isClass() && type.isStructureKnown() && !type.isBinary();
    }

    private String fieldNameFromSetter(IMethod method) {
        return fieldNameFromSetterName(method.getElementName());
    }

    private String fieldNameFromSetterName(String setterName) {
        String fieldName = setterName.substring(SETTER_PREFIX.length());

        return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    private String pluralToSingle(String name) {
        String elementName;

        if (name.endsWith("Houses")) {
            elementName = name.substring(0, name.length() - 1);
        } else if (name.endsWith("ses")) {
            elementName = name.substring(0, name.length() - 2);
        } else if (name.endsWith("ies")) {
            elementName = name.substring(0, name.length() - 3) + "y";
        } else if (name.endsWith("ves")) {
            elementName = name.substring(0, name.length() - 3) + "f";
        } else if (name.endsWith("ees")) {
            elementName = name.substring(0, name.length() - 1);
        } else if (name.endsWith("s")) {
            elementName = name.substring(0, name.length() - 1);
        } else {
            elementName = name + "Element";
        }

        return elementName;
    }
}
