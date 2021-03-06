/**
 * Copyright 2013 DuraSpace, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fcrepo.http.api;

import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static org.fcrepo.http.commons.test.util.PathSegmentImpl.createPathList;
import static org.fcrepo.http.commons.test.util.TestHelpers.getUriInfoImpl;
import static org.fcrepo.http.commons.test.util.TestHelpers.mockDatastream;
import static org.fcrepo.http.commons.test.util.TestHelpers.mockSession;
import static org.fcrepo.http.commons.test.util.TestHelpers.setField;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.modeshape.jcr.api.JcrConstants.JCR_CONTENT;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.version.VersionManager;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.fcrepo.kernel.Datastream;
import org.fcrepo.kernel.exception.InvalidChecksumException;
import org.fcrepo.kernel.identifiers.PidMinter;
import org.fcrepo.kernel.services.DatastreamService;
import org.fcrepo.kernel.services.NodeService;
import org.fcrepo.kernel.services.VersionService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class FedoraContentTest {

    FedoraContent testObj;

    @Mock
    private DatastreamService mockDatastreams;

    @Mock
    private NodeService mockNodeService;

    @Mock
    private VersionService mockVersions;

    @Mock
    private Session mockSession;

    @Mock
    private Node mockNode;

    @Mock
    private Node mockContentNode;

    @Mock
    private PidMinter mockMinter;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        testObj = new FedoraContent();
        setField(testObj, "datastreamService", mockDatastreams);
        setField(testObj, "nodeService", mockNodeService);
        setField(testObj, "uriInfo", getUriInfoImpl());
        setField(testObj, "versionService", mockVersions);
        mockSession = mockSession(testObj);
        setField(testObj, "session", mockSession);
        final Workspace mockWorkspace = mock(Workspace.class);
        when(mockWorkspace.getName()).thenReturn("default");
        when(mockSession.getWorkspace()).thenReturn(mockWorkspace);
        final VersionManager mockVM = mock(VersionManager.class);
        when(mockWorkspace.getVersionManager()).thenReturn(mockVM);
    }

    @Test
    public void testPutContent() throws RepositoryException, IOException,
                                InvalidChecksumException, URISyntaxException {
        final String pid = "FedoraDatastreamsTest1";
        final String dsId = "testDS";
        final String dsContent = "asdf";
        final String dsPath = "/" + pid + "/" + dsId;
        final InputStream dsContentStream = IOUtils.toInputStream(dsContent);
        when(mockNode.isNew()).thenReturn(true);
        when(mockNode.getNode(JCR_CONTENT)).thenReturn(mockContentNode);
        when(mockContentNode.getPath()).thenReturn(dsPath + "/jcr:content");
        when(mockNodeService.exists(mockSession, dsPath)).thenReturn(false);
        when(
                mockDatastreams.createDatastreamNode(any(Session.class),
                        eq(dsPath), anyString(), any(InputStream.class), eq((URI)null)))
                .thenReturn(mockNode);
        when(mockDatastreams.exists(mockSession, dsPath)).thenReturn(true);
        final Response actual =
            testObj.modifyContent(createPathList(pid, dsId), null, null,
                    dsContentStream, null);
        assertEquals(CREATED.getStatusCode(), actual.getStatus());
        verify(mockDatastreams).createDatastreamNode(any(Session.class),
                eq(dsPath), anyString(), any(InputStream.class), eq((URI)null));
        verify(mockSession).save();
    }

    @Test
    public void testCreateContent() throws RepositoryException, IOException,
                                   InvalidChecksumException, URISyntaxException {
        final String pid = "FedoraDatastreamsTest1";
        final String dsId = "xyz";
        final String dsContent = "asdf";
        final String dsPath = "/" + pid + "/" + dsId;
        final InputStream dsContentStream = IOUtils.toInputStream(dsContent);
        when(mockNode.isNew()).thenReturn(true);
        when(mockNode.getNode(JCR_CONTENT)).thenReturn(mockContentNode);
        when(mockContentNode.getPath()).thenReturn(dsPath + "/jcr:content");
        when(mockNodeService.exists(mockSession, dsPath)).thenReturn(false);
        when(
                mockDatastreams.createDatastreamNode(any(Session.class),
                        eq(dsPath), anyString(), any(InputStream.class),
                        eq((URI) null))).thenReturn(mockNode);
        when(mockDatastreams.exists(mockSession, dsPath)).thenReturn(true);
        final Response actual =
            testObj.create(createPathList(pid, dsId), null, null, TEXT_PLAIN_TYPE,
                    dsContentStream);
        assertEquals(CREATED.getStatusCode(), actual.getStatus());
        verify(mockDatastreams).createDatastreamNode(mockSession, dsPath,
                "text/plain", dsContentStream, null);
        verify(mockSession).save();
    }

    @Test
    public void testCreateContentAtMintedPath() throws RepositoryException,
                                               IOException,
                                               InvalidChecksumException,
                                               URISyntaxException,
                                               NoSuchFieldException {
        final String pid = "FedoraDatastreamsTest1";
        final String dsContent = "asdf";
        final String dsPath = "/" + pid;
        final InputStream dsContentStream = IOUtils.toInputStream(dsContent);
        when(mockNodeService.exists(mockSession, dsPath)).thenReturn(true);
        when(mockMinter.mintPid()).thenReturn("xyz");
        setField(testObj, "pidMinter", mockMinter);
        when(mockNode.isNew()).thenReturn(true);
        when(mockNode.getNode(JCR_CONTENT)).thenReturn(mockContentNode);
        when(mockContentNode.getPath()).thenReturn(dsPath + "xyz/jcr:content");
        when(
                mockDatastreams.createDatastreamNode(any(Session.class), eq("/"
                        + pid + "/xyz"), anyString(), any(InputStream.class),
                        eq((URI) null))).thenReturn(mockNode);
        when(mockDatastreams.exists(mockSession, dsPath)).thenReturn(true);
        final Response actual =
            testObj.create(createPathList(pid), null, null, TEXT_PLAIN_TYPE,
                    dsContentStream);
        assertEquals(CREATED.getStatusCode(), actual.getStatus());
        verify(mockDatastreams).createDatastreamNode(mockSession,
                "/" + pid + "/xyz", "text/plain", dsContentStream, null);
        verify(mockSession).save();
    }


    @Test
    public void testCreateContentWithSlug() throws RepositoryException,
                                                           IOException,
                                                           InvalidChecksumException,
                                                           URISyntaxException,
                                                           NoSuchFieldException {
        final String pid = "FedoraDatastreamsTest1";
        final String dsid = "slug";
        final String dsContent = "asdf";
        final String dsPath = "/" + pid;
        final InputStream dsContentStream = IOUtils.toInputStream(dsContent);
        when(mockNodeService.exists(mockSession, dsPath)).thenReturn(true);
        setField(testObj, "pidMinter", mockMinter);
        when(mockNode.isNew()).thenReturn(true);
        when(mockNode.getNode(JCR_CONTENT)).thenReturn(mockContentNode);
        when(mockContentNode.getPath()).thenReturn(dsPath + "slug/jcr:content");
        when(
                mockDatastreams.createDatastreamNode(any(Session.class), eq("/"
                                                                                + pid + "/slug"), anyString(), any(InputStream.class),
                                                        eq((URI) null))).thenReturn(mockNode);
        when(mockDatastreams.exists(mockSession, dsPath)).thenReturn(true);
        final Response actual =
            testObj.create(createPathList(pid), dsid, null, TEXT_PLAIN_TYPE,
                              dsContentStream);
        assertEquals(CREATED.getStatusCode(), actual.getStatus());
        verify(mockDatastreams).createDatastreamNode(mockSession,
                                                        "/" + pid + "/slug", "text/plain", dsContentStream, null);
        verify(mockSession).save();
    }

    @Test
    public void testModifyContent() throws RepositoryException, IOException,
                                   InvalidChecksumException, URISyntaxException {
        final String pid = "FedoraDatastreamsTest1";
        final String dsId = "testDS";
        final String dsContent = "asdf";
        final String dsPath = "/" + pid + "/" + dsId;
        final URI checksum = new URI("urn:sha1:some-checksum");
        final InputStream dsContentStream = IOUtils.toInputStream(dsContent);
        when(mockNode.isNew()).thenReturn(false);
        final Datastream mockDs = mockDatastream(pid, dsId, dsContent);
        when(mockDatastreams.getDatastream(mockSession, dsPath)).thenReturn(
                mockDs);
        final Request mockRequest = mock(Request.class);
        when(
                mockRequest.evaluatePreconditions(any(Date.class),
                        any(EntityTag.class))).thenReturn(null);
        when(
                mockDatastreams.createDatastreamNode(any(Session.class),
                        eq(dsPath), anyString(), any(InputStream.class), eq(checksum)))
                .thenReturn(mockNode);
        when(mockDatastreams.exists(mockSession, dsPath)).thenReturn(true);
        final Response actual =
            testObj.modifyContent(createPathList(pid, dsId), "urn:sha1:some-checksum", null,
                    dsContentStream, mockRequest);
        assertEquals(NO_CONTENT.getStatusCode(), actual.getStatus());
        verify(mockDatastreams).createDatastreamNode(any(Session.class),
                eq(dsPath), anyString(), any(InputStream.class), eq(checksum));
        verify(mockSession).save();
    }

    @Test
    public void testGetContent() throws RepositoryException, IOException {
        final String pid = "FedoraDatastreamsTest1";
        final String dsId = "testDS";
        final String path = "/" + pid + "/" + dsId;
        final String dsContent = "asdf";
        final Datastream mockDs = mockDatastream(pid, dsId, dsContent);
        when(mockDatastreams.getDatastream(mockSession, path)).thenReturn(
                mockDs);
        when(mockDs.getNode()).thenReturn(mockNode);
        when(mockNode.getPath()).thenReturn(path);
        final Request mockRequest = mock(Request.class);
        final Response actual =
            testObj.getContent(createPathList(pid, dsId), null, mockRequest);
        verify(mockDs).getContent();
        verify(mockSession, never()).save();
        final String actualContent =
            IOUtils.toString((InputStream) actual.getEntity());
        assertEquals("http://localhost/fcrepo" + path + ";rel=\"meta\"", actual
                .getMetadata().getFirst("Link"));
        assertEquals("asdf", actualContent);
    }

}