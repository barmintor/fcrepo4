/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.kernel.modeshape.rdf.impl;

import static com.google.common.collect.Lists.newArrayList;
import static com.hp.hpl.jena.rdf.model.ResourceFactory.createResource;

import com.google.common.collect.Lists;

import org.fcrepo.kernel.api.models.FedoraResource;
import org.fcrepo.kernel.api.exception.RepositoryRuntimeException;
import org.fcrepo.kernel.api.functions.CompositeConverter;
import org.fcrepo.kernel.api.functions.Converter;

import com.hp.hpl.jena.rdf.model.Resource;

import org.fcrepo.kernel.modeshape.identifiers.HashConverter;
import org.fcrepo.kernel.modeshape.identifiers.IdentifierConverter;
import org.fcrepo.kernel.modeshape.identifiers.NamespaceConverter;
import org.fcrepo.kernel.modeshape.identifiers.NodeResourceConverter;
import org.fcrepo.kernel.modeshape.identifiers.PathToNodeConverter;

import javax.jcr.Node;
import javax.jcr.Session;

import java.util.List;

/**
 * A very simple {@link IdentifierConverter} which translates JCR paths into Fedora subjects with
 * a configurable resource namespace (e.g., a baseURL).  When a REST API context is available for
 * constructing URIs, org.fcrepo.http.commons.api.rdf.HttpResourceConverter should be used instead.
 *
 * @author barmintor
 * @author ajs6f
 * @author escowles
 * @since 2015-04-24
 */
public class PrefixingIdentifierTranslator extends IdentifierConverter<Resource, String> {


    private static final NodeResourceConverter nodeResourceConverter = new NodeResourceConverter();

    private final String resourceNamespace;
    private final Session session;

    /**
     * Construct the graph with the provided resource namespace, which will translate JCR paths into
     * URIs prefixed with that namespace.  Should only be used when a REST API context is not available
     * for constructing URIs.
     * @param session Session to lookup nodes
     * @param resourceNamespace Resource namespace (i.e., base URL)
    **/
    public PrefixingIdentifierTranslator(final Session session, final String resourceNamespace) {
        this.session = session;
        this.resourceNamespace = resourceNamespace;
        setTranslationChain();
    }


    protected Converter<String, String> forward = identity();
    protected Converter<String, String> reverse = identity();

    /*
     * TODO: much of what happens with chains of translators inside these converters should be factored
     * out into some abstract class, or post Java 8, default implementation.
     */
    private void setTranslationChain() {

        for (final Converter<String, String> t : minimalTranslationChain) {
            forward = forward.andThen(t);
        }
        for (final Converter<String, String> t : Lists.reverse(minimalTranslationChain)) {
            reverse = reverse.andThen(t.inverse());
        }
    }


    @SuppressWarnings("unchecked")
    private static final List<Converter<String, String>> minimalTranslationChain =
            newArrayList((Converter<String, String>) new NamespaceConverter(),
                    (Converter<String, String>) new HashConverter()
            );

    @Override
    public String apply(final Resource subject) {
        if (!inDomain(subject)) {
            throw new RepositoryRuntimeException("Subject " + subject + " is not in this repository");
        }

        return asString(subject);
    }

    @Override
    public boolean inDomain(final Resource subject) {
        return subject.isURIResource() && subject.getURI().startsWith(resourceNamespace);
    }

    @Override
    public Resource toDomain(final String absPath) {
        final String relativePath;

        if (absPath.startsWith("/")) {
            relativePath = absPath.substring(1);
        } else {
            relativePath = absPath;
        }
        return createResource(resourceNamespace + reverse.apply(relativePath));
    }

    @Override
    public String asString(final Resource subject) {
        if (!inDomain(subject)) {
            return null;
        }

        final String path = subject.getURI().substring(resourceNamespace.length() - 1);

        final String absPath = forward.apply(path);

        if (absPath.isEmpty()) {
            return "/";
        }
        return absPath;
    }

    @Override
    public <C> Converter<Resource, C> andThen(final Converter<String, C> after) {
        return new CompositeConverter<>(this, after);
    }

    @Override
    public <C> Converter<C, String> compose(final Converter<C, Resource> before) {
        return new CompositeConverter<>(before, this);
    }

    /**
     * Convenience factory method
     * @return
     */
    public Converter<Resource, Node> toNodes() {
        return this.andThen(new PathToNodeConverter(session));
    }

    /**
     * Convenience factory method
     * @return
     */
    public Converter<Resource, FedoraResource> toResources() {
        return toNodes().andThen(nodeResourceConverter);
    }
}
