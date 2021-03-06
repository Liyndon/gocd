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

package com.thoughtworks.go.plugin.access.analytics;

import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.domain.analytics.AnalyticsData;
import com.thoughtworks.go.plugin.infra.PluginManager;
import org.hamcrest.core.Is;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.skyscreamer.jsonassert.JSONAssert;

import java.util.Arrays;
import java.util.HashMap;

import static com.thoughtworks.go.plugin.access.analytics.AnalyticsPluginConstants.*;
import static com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class AnalyticsExtensionTest {
    public static final String PLUGIN_ID = "plugin-id";

    @Mock
    private PluginManager pluginManager;
    private ArgumentCaptor<GoPluginApiRequest> requestArgumentCaptor;
    private AnalyticsExtension analyticsExtension;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        when(pluginManager.resolveExtensionVersion(PLUGIN_ID, Arrays.asList("1.0"))).thenReturn("1.0");
        when(pluginManager.isPluginOfType(AnalyticsPluginConstants.EXTENSION_NAME, PLUGIN_ID)).thenReturn(true);

        analyticsExtension = new AnalyticsExtension(pluginManager);

        requestArgumentCaptor = ArgumentCaptor.forClass(GoPluginApiRequest.class);
    }

    @Test
    public void shouldTalkToPlugin_To_GetCapabilities() throws Exception {
        String responseBody = "{\"supports_pipeline_analytics\":\"true\"}";
        when(pluginManager.submitTo(eq(PLUGIN_ID), requestArgumentCaptor.capture())).thenReturn(new DefaultGoPluginApiResponse(SUCCESS_RESPONSE_CODE, responseBody));

        com.thoughtworks.go.plugin.domain.analytics.Capabilities capabilities = analyticsExtension.getCapabilities(PLUGIN_ID);

        assertRequest(requestArgumentCaptor.getValue(), AnalyticsPluginConstants.EXTENSION_NAME, "1.0", REQUEST_GET_CAPABILITIES, null);
        assertThat(capabilities.supportsPipelineAnalytics(), is(true));
    }

    @Test
    public void shouldTalkToPlugin_To_GetPipelineAnalytics() throws Exception {
        String responseBody = "{ \"view_path\": \"path/to/view\", \"data\": \"{}\" }";
        when(pluginManager.submitTo(eq(PLUGIN_ID), requestArgumentCaptor.capture())).thenReturn(new DefaultGoPluginApiResponse(SUCCESS_RESPONSE_CODE, responseBody));

        AnalyticsData pipelineAnalytics = analyticsExtension.getPipelineAnalytics(PLUGIN_ID, "test_pipeline");

        assertRequest(requestArgumentCaptor.getValue(), AnalyticsPluginConstants.EXTENSION_NAME, "1.0", REQUEST_GET_PIPELINE_ANALYTICS, "{\"pipeline_name\": \"test_pipeline\"}");

        assertThat(pipelineAnalytics.getViewPath(), is("path/to/view"));
        assertThat(pipelineAnalytics.getData(), is("{}"));
    }

    @Test
    public void shouldFetchStaticAssets() throws Exception {
        String responseBody = "{ \"assets\": \"assets payload\" }";
        when(pluginManager.submitTo(eq(PLUGIN_ID), requestArgumentCaptor.capture())).thenReturn(new DefaultGoPluginApiResponse(SUCCESS_RESPONSE_CODE, responseBody));
        String assets = analyticsExtension.getStaticAssets(PLUGIN_ID);

        assertRequest(requestArgumentCaptor.getValue(), AnalyticsPluginConstants.EXTENSION_NAME, "1.0", REQUEST_GET_STATIC_ASSETS, null);

        assertThat(assets, is("assets payload"));
    }

    @Test
    public void shouldErrorOutInAbsenceOfStaticAssets() throws Exception {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("No assets defined!");

        when(pluginManager.submitTo(eq(PLUGIN_ID), requestArgumentCaptor.capture())).thenReturn(new DefaultGoPluginApiResponse(SUCCESS_RESPONSE_CODE, "{}"));

        analyticsExtension.getStaticAssets(PLUGIN_ID);
    }

    @Test
    public void shouldSerializePluginSettingsToJSON() throws Exception {
        String pluginId = "plugin_id";
        HashMap<String, String> pluginSettings = new HashMap<>();
        pluginSettings.put("key1", "val1");
        pluginSettings.put("key2", "val2");

        when(pluginManager.resolveExtensionVersion(pluginId, analyticsExtension.goSupportedVersions())).thenReturn("1.0");

        String pluginSettingsJSON = analyticsExtension.pluginSettingsJSON(pluginId, pluginSettings);

        assertThat(pluginSettingsJSON, Is.is("{\"key1\":\"val1\",\"key2\":\"val2\"}"));
    }

    private void assertRequest(GoPluginApiRequest goPluginApiRequest, String extensionName, String version, String requestName, String requestBody) throws JSONException {
        assertThat(goPluginApiRequest.extension(), Is.is(extensionName));
        assertThat(goPluginApiRequest.extensionVersion(), Is.is(version));
        assertThat(goPluginApiRequest.requestName(), Is.is(requestName));
        JSONAssert.assertEquals(requestBody, goPluginApiRequest.requestBody(), true);
    }
}