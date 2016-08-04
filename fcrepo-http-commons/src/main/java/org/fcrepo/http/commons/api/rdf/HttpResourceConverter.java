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
package org.fcrepo.http.commons.api.rdf;

import static com.hp.hpl.jena.rdf.model.ResourceFactory.createResource;
import static java.util.Collections.singleton;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.replaceOnce;
import static org.fcrepo.kernel.api.FedoraTypes.FCR_VERSIONS;
import static org.fcrepo.kernel.modeshape.identifiers.NodeResourceConverter.nodeConverter;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionHistory;
import javax.ws.rs.core.UriBuilder;

import org.fcrepo.kernel.api.exception.InvalidResourceIdentifierException;
import org.fcrepo.kernel.api.exception.RepositoryRuntimeException;
import org.fcrepo.kernel.api.functions.Converter;
import org.fcrepo.kernel.api.models.FedoraResource;
import org.fcrepo.kernel.modeshape.identifiers.IdentifierConverter;
import org.fcrepo.kernel.modeshape.identifiers.InternalPathToNodeConverter;

import org.glassfish.jersey.uri.UriTemplate;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;

import com.hp.hpl.jena.rdf.model.Resource;

/**
 * Convert between Jena Resources and JCR Nodes using a JAX-RS UriBuilder to mediate the
 * URI translation.
 *
 * @author cabeer
 * @since 10/5/14
 */
public class HttpResourceConverter extends IdentifierConverter<Resource,String> {

    private static final Logger LOGGER = getLogger(HttpResourceConverter.class);

    protected List<Converter<String, String>> translationChain;

    private final Session session;
    private final UriBuilder uriBuilder;

    protected Converter<String, String> pathProcessor = identity();

    private final UriTemplate uriTemplate;

    /**
     * Create a new identifier converter within the given session with the given URI template
     * @param session the session
     * @param uriBuilder the uri builder
     */
    public HttpResourceConverter(final Session session,
                                 final UriBuilder uriBuilder) {

        this.session = session;
        this.uriBuilder = uriBuilder;
        this.uriTemplate = new UriTemplate(uriBuilder.toTemplate());

        resetTranslationChain();
    }

    private UriBuilder uriBuilder() {
        return UriBuilder.fromUri(uriBuilder.toTemplate());
    }

    @Override
    public String apply(final Resource resource) {
        return asString(resource);
    }

    @Override
    public boolean inDomain(final Resource resource) {
        final Map<String, String> values = new HashMap<>();

        return uriTemplate.match(resource.getURI(), values) && values.containsKey("path") ||
            isRootWithoutTrailingSlash(resource);
    }

    @Override
    public Resource toDomain(final String path) {

        String realPath;
        if (path == null) {
            realPath = "";
        } else if (path.startsWith("/")) {
            realPath = pathProcessor.toDomain(path);
        } else {
            realPath = pathProcessor.toDomain("/" + path);
        }

        // the path must not start with a slash '/' to append correctly to the base
        if (realPath.startsWith("/")) {
            realPath = replaceOnce(realPath,"/",EMPTY);
        }
        final UriBuilder uri = uriBuilder();

        if (realPath.contains("#")) {

            final String[] split = realPath.split("#", 2);

            uri.resolveTemplate("path", split[0], false);
            uri.fragment(split[1]);
        } else {
            uri.resolveTemplate("path", realPath, false);

        }
        return createResource(uri.build().toString());
    }

    @Override
    public String asString(final Resource resource) {
        final Map<String, String> values = new HashMap<>();

        return asString(resource, values);
    }

    /**
     * Convert the incoming Resource to a JCR path (but don't attempt to load the node).
     *
     * @param resource Jena Resource to convert
     * @param values a map that will receive the matching URI template variables for future use.
     * @return
     */
    private String asString(final Resource resource, final Map<String, String> values) {
        if (uriTemplate.match(resource.getURI(), values) && values.containsKey("path")) {
            String path = "/" + values.get("path");

            path = pathProcessor.apply(path);

            if (path == null) {
                return null;
            }

            try {
                path = URLDecoder.decode(path, "UTF-8");
            } catch (final UnsupportedEncodingException e) {
                LOGGER.debug("Unable to URL-decode path " + e + " as UTF-8", e);
            }

            if (path.isEmpty()) {
                return "/";
            }

            // Validate path
            if (path.contains("//")) {
                throw new InvalidResourceIdentifierException("Path contains empty element! " + path);
            }
            return path;
        }

        if (isRootWithoutTrailingSlash(resource)) {
            return "/";
        }

        return null;
    }


    @SuppressWarnings("unused")
    private Node getNode(final String path) throws RepositoryException {
        if (path.contains(FCR_VERSIONS)) {
            final String[] split = path.split("/" + FCR_VERSIONS + "/", 2);
            final String versionedPath = split[0];
            final String versionAndPathIntoVersioned = split[1];
            final String[] split1 = versionAndPathIntoVersioned.split("/", 2);
            final String version = split1[0];

            final String pathIntoVersioned;
            if (split1.length > 1) {
                pathIntoVersioned = split1[1];
            } else {
                pathIntoVersioned = "";
            }

            final Node node = getFrozenNodeByLabel(versionedPath, version);

            if (pathIntoVersioned.isEmpty()) {
                return node;
            } else if (node != null) {
                return node.getNode(pathIntoVersioned);
            } else {
                throw new PathNotFoundException("Unable to find versioned resource at " + path);
            }
        }
        try {
            return session.getNode(path);
        } catch (IllegalArgumentException ex) {
            throw new InvalidResourceIdentifierException("Illegal path: " + path);
        }
    }

    /**
     * A private helper method that tries to look up frozen node for the given subject
     * by a label.  That label may either be one that was assigned at creation time
     * (and is a version label in the JCR sense) or a system assigned identifier that
     * was used for versions created without a label.  The current implementation
     * uses the JCR UUID for the frozen node as the system-assigned label.
     */
    private Node getFrozenNodeByLabel(final String baseResourcePath, final String label) {
        try {
            final Node n = getNode(baseResourcePath, label);

            if (n != null) {
                return n;
            }

             /*
             * Though a node with an id of the label was found, it wasn't the
             * node we were looking for, so fall through and look for a labeled
             * node.
             */
            final VersionHistory hist =
                    session.getWorkspace().getVersionManager().getVersionHistory(baseResourcePath);
            if (hist.hasVersionLabel(label)) {
                LOGGER.debug("Found version for {} by label {}.", baseResourcePath, label);
                return hist.getVersionByLabel(label).getFrozenNode();
            }
            LOGGER.warn("Unknown version {} with label or uuid {}!", baseResourcePath, label);
            throw new PathNotFoundException("Unknown version " + baseResourcePath
                    + " with label or uuid " + label);
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    private Node getNode(final String baseResourcePath, final String label) throws RepositoryException {
        try {
            final Node frozenNode = session.getNodeByIdentifier(label);

            /*
             * We found a node whose identifier is the "label" for the version.  Now
             * we must do due diligence to make sure it's a frozen node representing
             * a version of the subject node.
             */
            final Property p = frozenNode.getProperty("jcr:frozenUuid");
            if (p != null) {
                final Node subjectNode = session.getNode(baseResourcePath);
                if (p.getString().equals(subjectNode.getIdentifier())) {
                    return frozenNode;
                }
            }

        } catch (final ItemNotFoundException ex) {
            /*
             * the label wasn't a uuid of a frozen node but
             * instead possibly a version label.
             */
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static String getPath(final FedoraResource resource) {
        if (resource.isFrozenResource()) {
            // the versioned resource we're in
            final FedoraResource versionableFrozenResource = resource.getVersionedAncestor();

            // the unfrozen equivalent for the versioned resource
            final FedoraResource unfrozenVersionableResource = versionableFrozenResource.getUnfrozenResource();

            // the label for this version
            final String versionLabel = versionableFrozenResource.getVersionLabelOfFrozenResource();

            // the path to this resource within the versioning tree
            final String pathWithinVersionable;

            if (!resource.equals(versionableFrozenResource)) {
                pathWithinVersionable = getRelativePath(resource, versionableFrozenResource);
            } else {
                pathWithinVersionable = "";
            }

            // and, finally, the path we want to expose in the URI
            final String path = unfrozenVersionableResource.getPath()
                    + "/" + FCR_VERSIONS
                    + (versionLabel != null ? "/" + versionLabel : "")
                    + pathWithinVersionable;
            return path.startsWith("/") ? path : "/" + path;
        }
        return resource.getPath();
    }

    private static String getRelativePath(final FedoraResource child, final FedoraResource ancestor) {
        return child.getPath().substring(ancestor.getPath().length());
    }

    protected void resetTranslationChain() {
        if (translationChain == null) {
            translationChain = getTranslationChain();
            final List<Converter<String, String>> newChain =
                    new ArrayList<>(singleton(new TransactionIdentifierConverter(session)));
            newChain.addAll(translationChain);
            setTranslationChain(newChain);
        }
    }

    private void setTranslationChain(final List<Converter<String, String>> chained) {

        translationChain = chained;

        pathProcessor = new ExternalPathToInternalPathConverter(translationChain);
    }


    protected List<Converter<String,String>> getTranslationChain() {
        final ApplicationContext context = getApplicationContext();
        if (context != null) {
            @SuppressWarnings("unchecked")
            final List<Converter<String,String>> tchain =
                    getApplicationContext().getBean("translationChain", List.class);
            return tchain;
        }
        return ExternalPathToInternalPathConverter.defaultList();
    }

    protected ApplicationContext getApplicationContext() {
        return getCurrentWebApplicationContext();
    }

    private boolean isRootWithoutTrailingSlash(final Resource resource) {
        final Map<String, String> values = new HashMap<>();

        return uriTemplate.match(resource.getURI() + "/", values) && values.containsKey("path") &&
            values.get("path").isEmpty();
    }

    /**
     * 
     * @return
     */
    public IdentifierConverter<Resource, Node> toNodes() {
        return this.andThen(new InternalPathToNodeConverter(session));
    }

    /**
     * 
     * @return
     */
    public IdentifierConverter<Resource, FedoraResource> toResources() {
        return toNodes().andThen(nodeConverter);
    }
}
