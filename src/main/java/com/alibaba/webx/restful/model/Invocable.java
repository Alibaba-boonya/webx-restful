/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.core.GenericType;

import com.alibaba.webx.restful.util.ClassTypePair;
import com.alibaba.webx.restful.util.ReflectionUtils;

/**
 * A common interface for invocable resource components. This includes resource methods, sub-resource methods and
 * sub-resource locators bound to a concrete handler class and a Java method (either directly or indirectly) declared &
 * implemented by the handler class.
 * <p/>
 * Invocable component information is used at runtime by a Java method dispatcher when processing requests.
 * 
 * @author Marek Potociar (marek.potociar at oracle.com)
 * @see ResourceMethod
 * @see ResourceMethodDispatcher
 */
public final class Invocable implements Parameterized {

    /**
     * Create a new resource method invocable model. Parameter values will be automatically decoded.
     * 
     * @param handler resource method handler.
     * @param handlingMethod handling Java method.
     */
    public static Invocable create(MethodHandler handler, Method handlingMethod) {
        return new Invocable(handler, handlingMethod, false);
    }

    /**
     * Create a new resource method invocable model.
     * 
     * @param handler resource method handler.
     * @param handlingMethod handling Java method.
     * @param encodedParameters {@code true} if the automatic parameter decoding should be disabled, false otherwise.
     */
    public static Invocable create(MethodHandler handler, Method handlingMethod, boolean encodedParameters) {
        return new Invocable(handler, handlingMethod, encodedParameters);
    }

    private final MethodHandler   handler;
    private final Method          handlingMethod;
    private final List<Parameter> parameters;
    private final GenericType<?>  responseType;

    public Invocable(MethodHandler handler, Method handlingMethod, boolean encodedParameters){
        this.handler = handler;
        this.handlingMethod = handlingMethod;

        final Class<?> handlerClass = handler.getHandlerClass();
        final ClassTypePair ctPair = ReflectionUtils.resolveGenericType(handlerClass,
                                                                        handlingMethod.getDeclaringClass(),
                                                                        handlingMethod.getReturnType(),
                                                                        handlingMethod.getGenericReturnType());
        this.responseType = GenericType.of(ctPair.rawClass(), ctPair.type());

        this.parameters = Collections.unmodifiableList(Parameter.create(handlerClass,
                                                                        handlingMethod.getDeclaringClass(),
                                                                        handlingMethod, encodedParameters));
    }

    /**
     * Get the model of the resource method handler that will be used to invoke the {@link #getHandlingMethod() handling
     * resource method} on.
     * 
     * @return resource method handler model.
     */
    public MethodHandler getHandler() {
        return handler;
    }

    /**
     * Getter for the Java method
     * 
     * @return corresponding Java method
     */
    public Method getHandlingMethod() {
        return handlingMethod;
    }

    /**
     * Get the resource method response type.
     * <p/>
     * The returned value provides information about the raw Java class as well as the Type information that contains
     * additional generic declaration information for generic Java class types.
     * 
     * @return resource method response type information.
     */
    public GenericType<?> getResponseType() {
        return responseType;
    }

    @Override
    public boolean requiresEntity() {
        for (Parameter p : getParameters()) {
            if (Parameter.Source.ENTITY == p.getSource()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Parameter> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return "Invocable{" + "handler=" + handler + ", handlingMethod=" + handlingMethod + ", parameters="
               + parameters + ", responseType=" + responseType + '}';
    }
}
