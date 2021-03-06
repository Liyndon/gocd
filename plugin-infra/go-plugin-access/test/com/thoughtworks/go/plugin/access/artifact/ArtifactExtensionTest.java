/*
 * Copyright 2017 ThoughtWorks, Inc.
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

package com.thoughtworks.go.plugin.access.artifact;

import com.thoughtworks.go.plugin.access.artifact.model.PublishArtifactResponse;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.validation.ValidationError;
import com.thoughtworks.go.plugin.api.response.validation.ValidationResult;
import com.thoughtworks.go.plugin.domain.common.Metadata;
import com.thoughtworks.go.plugin.domain.common.PluginConfiguration;
import com.thoughtworks.go.plugin.infra.PluginManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static com.thoughtworks.go.plugin.access.artifact.ArtifactExtensionConstants.*;
import static com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE;
import static com.thoughtworks.go.plugin.domain.common.PluginConstants.ARTIFACT_EXTENSION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class ArtifactExtensionTest {

    public static final String PLUGIN_ID = "foo.plugin";
    private PluginManager pluginManager;
    private ArtifactExtension artifactExtension;
    private ArgumentCaptor<GoPluginApiRequest> requestArgumentCaptor;

    @Before
    public void setUp() throws Exception {
        pluginManager = mock(PluginManager.class);
        artifactExtension = new ArtifactExtension(pluginManager);
        requestArgumentCaptor = ArgumentCaptor.forClass(GoPluginApiRequest.class);


        when(pluginManager.isPluginOfType(ARTIFACT_EXTENSION, PLUGIN_ID)).thenReturn(true);
        when(pluginManager.resolveExtensionVersion(PLUGIN_ID, artifactExtension.goSupportedVersions())).thenReturn("1.0");
    }

    @Test
    public void shouldGetSupportedVersions() {
        final ArtifactExtension artifactExtension = new ArtifactExtension(null);

        assertThat(artifactExtension.goSupportedVersions(), containsInAnyOrder("1.0"));
    }

    @Test
    public void shouldRegisterMessageHandler() {
        final ArtifactExtension artifactExtension = new ArtifactExtension(null);

        assertTrue(artifactExtension.getMessageHandler(ArtifactMessageConverterV1.VERSION) instanceof ArtifactMessageConverterV1);
    }

    @Test
    public void shouldSubmitPublishArtifactRequest() {
        final String responseBody = "{\n" +
                "  \"metadata\": {\n" +
                "    \"artifact-version\": \"10.12.0\"\n" +
                "  },\n" +
                "  \"errors\": [\"foo\",\"bar\"]\n" +
                "}";

        when(pluginManager.submitTo(eq(PLUGIN_ID), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success(responseBody));

        final PublishArtifactResponse response = artifactExtension.publishArtifact(PLUGIN_ID, new HashMap<>());

        final GoPluginApiRequest request = requestArgumentCaptor.getValue();

        assertThat(request.extension(), is(ARTIFACT_EXTENSION));
        assertThat(request.requestName(), is(REQUEST_PUBLISH_ARTIFACT));
        assertThat(request.requestBody(), is("[]"));

        assertThat(response.getMetadata().size(), is(1));
        assertThat(response.getMetadata(), hasEntry("artifact-version", "10.12.0"));

        assertThat(response.getErrors(), hasSize(2));
        assertThat(response.getErrors(), contains("foo", "bar"));
    }

    @Test
    public void shouldGetArtifactStoreMetadataFromPlugin() {
        String responseBody = "[{\"key\":\"BUCKET_NAME\",\"metadata\":{\"required\":true,\"secure\":false}},{\"key\":\"AWS_ACCESS_KEY\",\"metadata\":{\"required\":true,\"secure\":true}}]";


        when(pluginManager.submitTo(eq(PLUGIN_ID), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success(responseBody));

        final List<PluginConfiguration> response = artifactExtension.getArtifactStoreMetadata(PLUGIN_ID);

        final GoPluginApiRequest request = requestArgumentCaptor.getValue();

        assertThat(request.extension(), is(ARTIFACT_EXTENSION));
        assertThat(request.requestName(), is(REQUEST_STORE_CONFIG_METADATA));
        assertNull(request.requestBody());

        assertThat(response.size(), is(2));
        assertThat(response, containsInAnyOrder(
                new PluginConfiguration("BUCKET_NAME", new Metadata(true, false)),
                new PluginConfiguration("AWS_ACCESS_KEY", new Metadata(true, true))
        ));
    }

    @Test
    public void shouldGetPublishArtifactMetadataFromPlugin() {
        String responseBody = "[{\"key\":\"FILENAME\",\"metadata\":{\"required\":true,\"secure\":false}},{\"key\":\"VERSION\",\"metadata\":{\"required\":true,\"secure\":true}}]";

        when(pluginManager.submitTo(eq(PLUGIN_ID), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success(responseBody));

        final List<PluginConfiguration> response = artifactExtension.getPublishArtifactMetadata(PLUGIN_ID);

        final GoPluginApiRequest request = requestArgumentCaptor.getValue();

        assertThat(request.extension(), is(ARTIFACT_EXTENSION));
        assertThat(request.requestName(), is(REQUEST_PUBLISH_ARTIFACT_METADATA));
        assertNull(request.requestBody());

        assertThat(response.size(), is(2));
        assertThat(response, containsInAnyOrder(
                new PluginConfiguration("FILENAME", new Metadata(true, false)),
                new PluginConfiguration("VERSION", new Metadata(true, true))
        ));
    }

    @Test
    public void shouldValidateArtifactStoreConfig() {
        String responseBody = "[{\"message\":\"ACCESS_KEY must not be blank.\",\"key\":\"ACCESS_KEY\"}]";

        when(pluginManager.submitTo(eq(PLUGIN_ID), requestArgumentCaptor.capture())).thenReturn(new DefaultGoPluginApiResponse(SUCCESS_RESPONSE_CODE, responseBody));

        ValidationResult validationResult = artifactExtension.validateArtifactStoreConfig(PLUGIN_ID, Collections.singletonMap("ACCESS_KEY", ""));

        final GoPluginApiRequest request = requestArgumentCaptor.getValue();

        assertThat(request.extension(), is(ARTIFACT_EXTENSION));
        assertThat(request.requestName(), is(REQUEST_STORE_CONFIG_VALIDATE));
        assertThat(request.requestBody(), is("{\"ACCESS_KEY\":\"\"}"));

        assertThat(validationResult.isSuccessful(), is(false));
        assertThat(validationResult.getErrors(), containsInAnyOrder(
                new ValidationError("ACCESS_KEY", "ACCESS_KEY must not be blank.")
        ));
    }

    @Test
    public void shouldValidatePluggableArtifactConfig() {
        String responseBody = "[{\"message\":\"Filename must not be blank.\",\"key\":\"Filename\"}]";

        when(pluginManager.submitTo(eq(PLUGIN_ID), requestArgumentCaptor.capture())).thenReturn(new DefaultGoPluginApiResponse(SUCCESS_RESPONSE_CODE, responseBody));

        ValidationResult validationResult = artifactExtension.validatePluggableArtifactConfig(PLUGIN_ID, Collections.singletonMap("Filename", ""));

        final GoPluginApiRequest request = requestArgumentCaptor.getValue();

        assertThat(request.extension(), is(ARTIFACT_EXTENSION));
        assertThat(request.requestName(), is(REQUEST_PUBLISH_ARTIFACT_VALIDATE));
        assertThat(request.requestBody(), is("{\"Filename\":\"\"}"));

        assertThat(validationResult.isSuccessful(), is(false));
        assertThat(validationResult.getErrors(), containsInAnyOrder(
                new ValidationError("Filename", "Filename must not be blank.")
        ));
    }
}