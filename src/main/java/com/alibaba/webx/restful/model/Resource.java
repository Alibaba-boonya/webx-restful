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

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Path;

import org.springframework.util.Assert;

import com.alibaba.webx.restful.uri.PathPattern;
import com.alibaba.webx.restful.util.IdentityHashSet;
import com.google.common.collect.Lists;

/**
 * Model of a single resource component.
 * <p>
 * Resource component model represents a collection of {@link ResourceMethod methods} grouped under the same parent
 * request path template. {@code Resource} class is also the main entry point to the programmatic resource modeling API
 * that provides ability to programmatically extend the existing JAX-RS annotated resource classes or build new resource
 * models that may be utilized by Jersey runtime.
 * </p>
 * <p>
 * For example:
 * 
 * <pre>
 * &#64;Path("hello")
 * public class HelloResource {
 *      &#64;GET
 *      &#64;Produces("text/plain")
 *      public String sayHello() {
 *          return "Hello!";
 *      }
 * }
 * 
 * ...
 * 
 * // Register the annotated resource.
 * ResourceConfig resourceConfig = new ResourceConfig(HelloResource.class);
 * 
 * // Add new "hello2" resource using the annotated resource class
 * // and overriding the resource path.
 * Resource.Builder resourceBuilder =
 *         Resource.builder(HelloResource.class, new LinkedList&lt;ResourceModelIssue&gt;())
 *         .path("hello2");
 * 
 * // Add a new (virtual) sub-resource method to the "hello2" resource.
 * resourceBuilder.addMethod("GET")
 *         .path("world")
 *         .produces("text/plain")
 *         .handledBy(new Inflector&lt;Request, String&gt;() {
 *                 &#64;Override
 *                 public String apply(Request request) {
 *                     return "Hello World!";
 *                 }
 *         });
 * 
 * // Register the new programmatic resource in the application's configuration.
 * resourceConfig.addResources(resourceBuilder.build());
 * </pre>
 * 
 * The following table illustrates the supported requests and provided responses for the application configured in the
 * example above.
 * <table>
 * <tr>
 * <th>Request</th>
 * <th>Response</th>
 * <th>Method invoked</th>
 * </tr>
 * <tr>
 * <td>{@code "GET /hello"}</td>
 * <td>{@code "Hello!"}</td>
 * <td>{@code HelloResource.sayHello()}</td>
 * </tr>
 * <tr>
 * <td>{@code "GET /hello2"}</td>
 * <td>{@code "Hello!"}</td>
 * <td>{@code HelloResource.sayHello()}</td>
 * </tr>
 * <tr>
 * <td>{@code "GET /hello2/world"}</td>
 * <td>{@code "Hello World!"}</td>
 * <td>{@code Inflector.apply()}</td>
 * </tr>
 * </table>
 * </p>
 * 
 * @author Marek Potociar (marek.potociar at oracle.com)
 */
public final class Resource implements Routed, ResourceModelComponent {

    /**
     * Resource model component builder.
     */
    public static final class Builder {

        private String                            name;
        private String                            path;
        private boolean                           isRoot;

        private final Set<ResourceMethod.Builder> methodBuilders;

        private final List<ResourceMethod>        resourceMethods;
        private final List<ResourceMethod>        subResourceMethods;
        private final List<ResourceMethod>        locators;

        private Builder(){
            this.name = "[unnamed]";

            this.methodBuilders = new IdentityHashSet<ResourceMethod.Builder>();

            this.resourceMethods = new LinkedList<ResourceMethod>();
            this.subResourceMethods = new LinkedList<ResourceMethod>();
            this.locators = new LinkedList<ResourceMethod>();
        }

        private Builder(final String path){
            this();
            path(path);
        }

        /**
         * Define a new name of the built resource. The resource model name is typically used for reporting purposes
         * (e.g. validation etc.).
         * 
         * @param name new name of the resource.
         * @return updated builder object.
         * @see org.glassfish.jersey.server.model.Resource#getName()
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Define a new path for the built resource.
         * <p/>
         * NOTE: Invoking this method marks a resource as a root resource.
         * 
         * @param path new path for the resource.
         * @return updated builder object.
         */
        public Builder path(String path) {
            this.path = path;
            // we need to maintain a separate flag because the entered path value
            // can be null.
            this.isRoot = true;
            return this;
        }

        /**
         * Add a new method model to the resource for processing requests of the specified HTTP method.
         * <p/>
         * The returned builder is automatically bound to the the resource. It is not necessary to invoke the
         * {@link ResourceMethod.Builder#build() build()} method on the method builder after setting all the data. This
         * will be done automatically when the resource is built.
         * 
         * @param httpMethod HTTP method that will be processed by the method.
         * @return a new resource method builder.
         */
        public ResourceMethod.Builder addMethod(String httpMethod) {
            ResourceMethod.Builder builder = new ResourceMethod.Builder(this);
            methodBuilders.add(builder);
            return builder.httpMethod(httpMethod);
        }

        /**
         * Add a new arbitrary method model to the resource.
         * <p/>
         * The returned builder is automatically bound to the the resource. It is not necessary to invoke the
         * {@link ResourceMethod.Builder#build() build()} method on the method builder after setting all the data. This
         * will be done automatically when the resource is built.
         * 
         * @return a new resource method builder.
         */
        public ResourceMethod.Builder addMethod() {
            ResourceMethod.Builder builder = new ResourceMethod.Builder(this);
            methodBuilders.add(builder);
            return builder;
        }

        /**
         * Merge methods from a given resource model into this resource model builder.
         * 
         * @param resource to be merged into this resource model builder.
         * @return updated builder object.
         */
        public Builder mergeWith(Resource resource) {
            this.resourceMethods.addAll(resource.getResourceMethods());
            this.subResourceMethods.addAll(resource.getSubResourceMethods());
            this.locators.addAll(resource.getSubResourceLocators());

            return this;
        }

        /**
         * Called when a new resource, sub-resource and sub-resource locator method was built and should be registered
         * with the resource builder.
         * <p>
         * This is a friend call-back API exposed for a use by a {@link ResourceMethod.Builder ResourceMethod.Builder}.
         * </p>
         * 
         * @param builder builder instance that built the method.
         * @param method new resource, sub-resource or sub-resource locator
         */
        void onBuildMethod(ResourceMethod.Builder builder, ResourceMethod method) {
            Assert.isTrue(methodBuilders.remove(builder),
                          "Resource.Builder.onBuildMethod() invoked from a resource method builder "
                                  + "that is not registered in the resource builder instance.");

            switch (method.getType()) {
                case RESOURCE_METHOD:
                    resourceMethods.add(method);
                    break;
                case SUB_RESOURCE_METHOD:
                    subResourceMethods.add(method);
                    break;
                case SUB_RESOURCE_LOCATOR:
                    locators.add(method);
                    break;
            }
        }

        /**
         * Build a new resource model.
         * 
         * @return new (immutable) resource model.
         */
        public Resource build() {
            // We have to iterate the set this way to prevent ConcurrentModificationExceptions
            // caused by the nested invocation of Set.remove(...) in Resource.Builder.onBuildMethod(...).
            while (!methodBuilders.isEmpty()) {
                methodBuilders.iterator().next().build();
            }

            return new Resource(name, path, isRoot, Collections.unmodifiableList(Lists.newArrayList(resourceMethods)),
                                Collections.unmodifiableList(Lists.newArrayList(subResourceMethods)),
                                Collections.unmodifiableList(Lists.newArrayList(locators)));
        }
    }

    /**
     * Get a new unbound resource model builder.
     * <p/>
     * Note that a resource built from the returned builder is <i>NOT</i> automatically marked to be a
     * {@link #isRootResource() root resource}. This can be changed by invoking the
     * {@link Builder#path(java.lang.String) path(...)} method on the returned builder.
     * 
     * @return new unbound resource model builder.
     * @see Resource.Builder#path(java.lang.String)
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get a new resource model builder for a resource bound to a given path.
     * <p/>
     * Note that a resource built from the returned builder is automatically marked to be a {@link #isRootResource()
     * root resource}.
     * 
     * @param path resource path.
     * @return new resource model builder.
     * @see Resource.Builder#path(java.lang.String)
     */
    public static Builder builder(final String path) {
        return new Builder(path);
    }

    /**
     * Create a resource model builder initialized by introspecting an annotated JAX-RS resource class.
     * <p/>
     * Method performs an {@link #isAcceptable(java.lang.Class) acceptability} check, on the resource class prior to the
     * resource model creation.
     * 
     * @param resourceClass resource class to be modelled.
     * @param issueList mutable list of issues that will be updated with the introspection-specific issues found in the
     * model.
     * @return resource model builder initialized by the class.
     * @throws IllegalArgumentException in case the class is not {@link #isAcceptable(java.lang.Class) acceptable} as a
     * JAX-RS resource.
     */
    public static Builder builder(Class<?> resourceClass, List<ResourceModelIssue> issueList)
                                                                                             throws IllegalArgumentException {
        return new IntrospectionModeller(resourceClass, issueList).createResourceBuilder(false);
    }

    /**
     * Create a resource model builder initialized by introspecting an annotated JAX-RS resource instance.
     * <p/>
     * Unlike {@link #builder(Class, java.util.List)}, this method does not perform the
     * {@link #isAcceptable(java.lang.Class) acceptability} check, since it is assumed that the instance of the resource
     * has already been created and is acceptable.
     * 
     * @param resource resource instance to be modelled.
     * @param issueList mutable list of issues that will be updated with the introspection-specific issues found in the
     * model.
     * @return resource model builder initialized by instance.
     */
    public static Builder builder(Object resource, List<ResourceModelIssue> issueList) {
        return new IntrospectionModeller(resource.getClass(), issueList).createResourceBuilder(true);
    }

    /**
     * Check if the class is acceptable as a JAX-RS provider or resource.
     * <p/>
     * Method returns {@code false} if the class is either
     * <ul>
     * <li>abstract</li>
     * <li>interface</li>
     * <li>annotation</li>
     * <li>primitive</li>
     * <li>local class</li>
     * <li>non-static member class</li>
     * </ul>
     * 
     * @param c class to be checked.
     * @return {@code true} if the class is an acceptable JAX-RS provider or resource, {@code false} otherwise.
     */
    public static boolean isAcceptable(Class<?> c) {
        return !((c.getModifiers() & Modifier.ABSTRACT) != 0 || c.isPrimitive() || c.isAnnotation() || c.isInterface()
                 || c.isLocalClass() || (c.isMemberClass() && (c.getModifiers() & Modifier.STATIC) == 0));
    }

    /**
     * Get the resource class {@link Path @Path} annotation.
     * <p/>
     * May return {@code null} in case there is no {@code @Path} annotation on the resource.
     * 
     * @param resourceClass resource class.
     * @return {@code @Path} annotation instance if present on the resource class (i.e. the class is a root resource
     * class), or {@code null} otherwise.
     */
    public static Path getPath(Class<?> resourceClass) {
        return IntrospectionModeller.getAnnotatedResourceClass(resourceClass).getAnnotation(Path.class);
    }

    /**
     * Get a new resource model builder initialized from a given resource model.
     * 
     * @param resource resource model initializing the resource builder.
     * @return new resource model builder.
     */
    public static Builder builder(Resource resource) {
        final Builder b = new Builder(resource.path);

        b.resourceMethods.addAll(resource.resourceMethods);
        b.subResourceMethods.addAll(resource.subResourceMethods);
        b.locators.addAll(resource.subResourceLocators);

        return b;
    }

    private final String               name;
    private final String               path;
    private final PathPattern          pathPattern;
    private final boolean              isRoot;
    private final List<ResourceMethod> resourceMethods;
    private final List<ResourceMethod> subResourceMethods;
    private final List<ResourceMethod> subResourceLocators;

    private Resource(final String name, final String path, final boolean isRoot,
                     final List<ResourceMethod> resourceMethods, final List<ResourceMethod> subResourceMethods,
                     final List<ResourceMethod> subResourceLocators){

        this.name = name;
        this.path = path;
        this.isRoot = isRoot;

        this.pathPattern = (!isRoot || path == null || path.isEmpty()) ? PathPattern.OPEN_ROOT_PATH_PATTERN : new PathPattern(
                                                                                                                              path,
                                                                                                                              PathPattern.RightHandPath.capturingZeroOrMoreSegments);

        this.resourceMethods = resourceMethods;
        this.subResourceMethods = subResourceMethods;
        this.subResourceLocators = subResourceLocators;
    }

    /**
     * Check if this resource model models a JAX-RS root resource.
     * 
     * @return {@code true}, if this is a model of a JAX-RS root resource, {@code false} otherwise.
     */
    public boolean isRootResource() {
        return isRoot;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public PathPattern getPathPattern() {
        return pathPattern;
    }

    /**
     * Get the resource name.
     * <p/>
     * If the resource was constructed from a JAX-RS annotated resource class, the resource name will be set to the
     * {@link Class#getName() fully-qualified name} of the resource class.
     * 
     * @return reference JAX-RS resource handler class.
     */
    public String getName() {
        return name;
    }

    /**
     * Provides a non-null list of resource methods available on the resource.
     * 
     * @return non-null abstract resource method list.
     */
    public List<ResourceMethod> getResourceMethods() {
        return resourceMethods;
    }

    /**
     * Provides a non-null list of sub-resource methods available on the resource.
     * 
     * @return non-null abstract sub-resource method list.
     */
    public List<ResourceMethod> getSubResourceMethods() {
        return subResourceMethods;
    }

    /**
     * Provides a non-null list of sub-resource locators available on the resource.
     * 
     * @return non-null abstract sub-resource locator list.
     */
    public List<ResourceMethod> getSubResourceLocators() {
        return subResourceLocators;
    }

    @Override
    public void accept(ResourceModelVisitor visitor) {
        visitor.visitResourceClass(this);
    }

    @Override
    public String toString() {
        return "Resource {" + ((path == null) ? "[unbound], " : "\"" + path + "\", ") + resourceMethods.size()
               + " resource methods, " + subResourceMethods.size() + " sub-resource methods, " + subResourceLocators
               + " sub-resource locators" + '}';
    }

    @Override
    public List<? extends ResourceModelComponent> getComponents() {
        List<ResourceMethod> components = new LinkedList<ResourceMethod>();

        components.addAll(getResourceMethods());
        components.addAll(getSubResourceMethods());
        components.addAll(getSubResourceLocators());

        return components;
    }
}
