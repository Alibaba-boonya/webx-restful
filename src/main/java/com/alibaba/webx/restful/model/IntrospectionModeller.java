/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.alibaba.webx.restful.model;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.Encoded;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.Suspend;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;

import com.alibaba.webx.restful.message.internal.LocalizationMessages;

/**
 * Utility class for constructing resource model from JAX-RS annotated POJO.
 * 
 * @author Jakub Podlesak (jakub.podlesak at oracle.com)
 */
final class IntrospectionModeller {

    private static final Logger            LOGGER = Logger.getLogger(IntrospectionModeller.class.getName());

    // introspected annotated JAX-RS resource class
    private final Class<?>                 handlerClass;
    private final List<ResourceModelIssue> issueList;

    /**
     * Create a new introspection modeller for a given JAX-RS resource class.
     * 
     * @param handlerClass JAX-RS resource (handler) class.
     * @param issueList a mutable list of resource model issues that will be updated with any introspection validation
     * issues found on the resource handler class during the {@link Resource.Builder resource builder} initialization.
     */
    public IntrospectionModeller(Class<?> handlerClass, List<ResourceModelIssue> issueList){
        this.handlerClass = handlerClass;
        this.issueList = issueList;
    }

    private void addFatalIssue(Object source, String message) {
        issueList.add(new ResourceModelIssue(source, message, true));
    }

    private void addMinorIssue(Object source, String message) {
        issueList.add(new ResourceModelIssue(source, message, false));
    }

    public Resource.Builder createResourceBuilder(boolean skipAcceptableCheck) throws IllegalArgumentException {
        if (!skipAcceptableCheck && !Resource.isAcceptable(handlerClass)) {
            addFatalIssue(handlerClass, LocalizationMessages.NON_INSTANTIATABLE_CLASS(handlerClass));
        }

        checkForNonPublicMethodIssues();

        final Class<?> annotatedResourceClass = getAnnotatedResourceClass(handlerClass);
        final Path rPathAnnotation = annotatedResourceClass.getAnnotation(Path.class);

        final boolean keepEncodedParams = (null != annotatedResourceClass.getAnnotation(Encoded.class));

        final List<MediaType> defaultConsumedTypes = extractMediaTypes(annotatedResourceClass.getAnnotation(Consumes.class));
        final List<MediaType> defaultProducedTypes = extractMediaTypes(annotatedResourceClass.getAnnotation(Produces.class));

        final MethodList methodList = new MethodList(handlerClass);

        checkResourceClassSetters(methodList, keepEncodedParams);
        checkResourceClassFields(keepEncodedParams);

        Resource.Builder resourceBuilder;

        if (null != rPathAnnotation) {
            resourceBuilder = Resource.builder(rPathAnnotation.value());
        } else {
            resourceBuilder = Resource.builder();
        }

        resourceBuilder.name(handlerClass.getName());

        addResourceMethods(resourceBuilder, methodList, keepEncodedParams, defaultConsumedTypes, defaultProducedTypes);
        addSubResourceMethods(resourceBuilder, methodList, keepEncodedParams, defaultConsumedTypes,
                              defaultProducedTypes);
        addSubResourceLocators(resourceBuilder, methodList, keepEncodedParams);

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(LocalizationMessages.NEW_AR_CREATED_BY_INTROSPECTION_MODELER(resourceBuilder.toString()));
        }

        return resourceBuilder;
    }

    private void checkForNonPublicMethodIssues() {
        final MethodList allDeclaredMethods = new MethodList(getAllDeclaredMethods(handlerClass));

        // non-public resource methods
        for (AnnotatedMethod m : allDeclaredMethods.withMetaAnnotation(HttpMethod.class).withoutAnnotation(Path.class).isNotPublic()) {
            addMinorIssue(handlerClass, LocalizationMessages.NON_PUB_RES_METHOD(m.getMethod().toGenericString()));
        }
        // non-public subres methods
        for (AnnotatedMethod m : allDeclaredMethods.withMetaAnnotation(HttpMethod.class).withAnnotation(Path.class).isNotPublic()) {
            addMinorIssue(handlerClass, LocalizationMessages.NON_PUB_SUB_RES_METHOD(m.getMethod().toGenericString()));
        }
        // non-public subres locators
        for (AnnotatedMethod m : allDeclaredMethods.withoutMetaAnnotation(HttpMethod.class).withAnnotation(Path.class).isNotPublic()) {
            addMinorIssue(handlerClass, LocalizationMessages.NON_PUB_SUB_RES_LOC(m.getMethod().toGenericString()));
        }
    }

    private void checkResourceClassSetters(final MethodList methodList, final boolean encodedFlag) {
        for (AnnotatedMethod method : methodList.withoutMetaAnnotation(HttpMethod.class).withoutAnnotation(Path.class).hasNumParams(1).hasReturnType(void.class).nameStartsWith("set")) {
            Parameter p = Parameter.create(handlerClass,
                                           method.getMethod().getDeclaringClass(),
                                           encodedFlag,
                                           GenericType.of(method.getParameterTypes()[0],
                                                          method.getGenericParameterTypes()[0]),
                                           method.getAnnotations());
            if (null != p) {
                BasicValidator.validateParameter(issueList, p, method.getMethod(),
                                                 method.getMethod().toGenericString(), "1");
            }
        }
    }

    private void checkResourceClassFields(final boolean encodedFlag) {
        for (Field field : handlerClass.getDeclaredFields()) {
            if (field.getDeclaredAnnotations().length > 0) {
                Parameter p = Parameter.create(handlerClass, field.getDeclaringClass(), encodedFlag,
                                               GenericType.of(field.getType(), field.getGenericType()),
                                               field.getAnnotations());
                if (null != p) {
                    BasicValidator.validateParameter(issueList, p, field, field.toGenericString(), field.getName());
                }
            }
        }
    }

    private List<Method> getAllDeclaredMethods(final Class<?> clazz) {
        final List<Method> result = new LinkedList<Method>();

        AccessController.doPrivileged(new PrivilegedAction<Object>() {

            @Override
            public Object run() {
                Class current = clazz;
                while (current != Object.class && current != null) {
                    result.addAll(Arrays.asList(current.getDeclaredMethods()));
                    current = current.getSuperclass();
                }
                return null;
            }
        });

        return result;
    }

    public static Class<?> getAnnotatedResourceClass(Class<?> resourceClass) {
        if (resourceClass.isAnnotationPresent(Path.class)) {
            return resourceClass;
        }

        for (Class<?> interfaceClass : resourceClass.getInterfaces()) {
            if (interfaceClass.isAnnotationPresent(Path.class)) {
                return interfaceClass;
            }
        }

        return resourceClass;
    }

    private static List<MediaType> resolveConsumedTypes(final AnnotatedMethod am,
                                                        final List<MediaType> defaultConsumedTypes) {
        // Override default types if the annotation is present on the method
        if (am.isAnnotationPresent(Consumes.class)) {
            return extractMediaTypes(am.getAnnotation(Consumes.class));
        }

        return defaultConsumedTypes;
    }

    private static List<MediaType> resolveProducedTypes(final AnnotatedMethod am,
                                                        final List<MediaType> defaultProducedTypes) {
        // Override default types if the annotation is present on the method
        if (am.isAnnotationPresent(Produces.class)) {
            return extractMediaTypes(am.getAnnotation(Produces.class));
        }

        return defaultProducedTypes;
    }

    private static List<MediaType> extractMediaTypes(final Consumes annotation) {
        return (annotation != null) ? extractMediaTypes(annotation.value()) : Collections.<MediaType> emptyList();
    }

    private static List<MediaType> extractMediaTypes(final Produces annotation) {
        return (annotation != null) ? extractMediaTypes(annotation.value()) : Collections.<MediaType> emptyList();
    }

    private static List<MediaType> extractMediaTypes(final String[] values) {
        if (values.length == 0) {
            return Collections.emptyList();
        }

        final List<MediaType> types = new ArrayList<MediaType>(values.length);
        for (final String mt : values) {
            types.add(MediaType.valueOf(mt));
        }

        return types;
    }

    private void addResourceMethods(Resource.Builder resourceBuilder, MethodList methodList, boolean encodedParameters,
                                    List<MediaType> defaultConsumedTypes, List<MediaType> defaultProducedTypes) {

        for (AnnotatedMethod am : methodList.withMetaAnnotation(HttpMethod.class).withoutAnnotation(Path.class)) {
            ResourceMethod.Builder methodBuilder = resourceBuilder.addMethod(am.getMetaMethodAnnotations(HttpMethod.class).get(0).value()).consumes(resolveConsumedTypes(am,
                                                                                                                                                                         defaultConsumedTypes)).produces(resolveProducedTypes(am,
                                                                                                                                                                                                                              defaultProducedTypes)).encodedParameters(encodedParameters).handledBy(handlerClass,
                                                                                                                                                                                                                                                                                                    am.getMethod());

        }
    }

    private void addSubResourceMethods(Resource.Builder resourceBuilder, MethodList methodList,
                                       boolean encodedParameters, List<MediaType> defaultConsumedTypes,
                                       List<MediaType> defaultProducedTypes) {

        for (AnnotatedMethod am : methodList.withMetaAnnotation(HttpMethod.class).withAnnotation(Path.class)) {
            ResourceMethod.Builder methodBuilder = resourceBuilder.addMethod(am.getMetaMethodAnnotations(HttpMethod.class).get(0).value()).path(am.getAnnotation(Path.class).value()).consumes(resolveConsumedTypes(am,
                                                                                                                                                                                                                    defaultConsumedTypes)).produces(resolveProducedTypes(am,
                                                                                                                                                                                                                                                                         defaultProducedTypes)).encodedParameters(encodedParameters).handledBy(handlerClass,
                                                                                                                                                                                                                                                                                                                                               am.getMethod());

        }
    }

    private void addSubResourceLocators(Resource.Builder resourceBuilder, MethodList methodList,
                                        boolean encodedParameters) {

        for (AnnotatedMethod am : methodList.withoutMetaAnnotation(HttpMethod.class).withAnnotation(Path.class)) {
            ResourceMethod.Builder methodBuilder = resourceBuilder.addMethod().path(am.getAnnotation(Path.class).value()).encodedParameters(encodedParameters).handledBy(handlerClass,
                                                                                                                                                                         am.getMethod());
        }
    }
}
