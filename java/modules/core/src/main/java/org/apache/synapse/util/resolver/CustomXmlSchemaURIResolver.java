/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.synapse.util.resolver;

import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.ws.commons.schema.resolver.URIResolver;
import org.xml.sax.InputSource;

import org.checkerframework.checker.startswith.qual.*;

/**
 * Class that adapts a {@link ResourceMap} to XmlSchemas's {@link URIResolver}.
 */
public class CustomXmlSchemaURIResolver implements URIResolver {
    private ResourceMap resourceMap;
    private SynapseConfiguration synCfg;

    public CustomXmlSchemaURIResolver() {
    }
    
    /**
     * Constructor.
     * 
     * @param resourceMap the resource map; may be null if no resource map is configured
     * @param synCfg the Synapse configuration
     */
    public CustomXmlSchemaURIResolver(ResourceMap resourceMap,
                                  SynapseConfiguration synCfg) {
        this();
        this.resourceMap = resourceMap;
        this.synCfg = synCfg;
    }
    
    /**
     * Resolve a schema import.
     * This method will first attempt to resolve the location using the configured
     * {@link ResourceMap} object. If this fails (because no {@link ResourceMap} is
     * configured or because {@link ResourceMap#resolve(SynapseConfiguration, String)}
     * returns null, it will resolve the location using
     * {@link SynapseConfigUtils#resolveRelativeURI(String, String)}.
     */
    @SuppressWarnings("startswith")//FALSE POSITIVE: method declaration can't override because of different annotations
                                   //on the parameters.
    public InputSource resolveEntity(String targetNamespace, @StartsWith({"https", "file"}) String schemaLocation,
                                                             @StartsWith({"https", "file"}) String baseUri) {
        InputSource result = null;
        if (resourceMap != null) {
            result = resourceMap.resolve(synCfg, schemaLocation);
        }
        if (result == null) {
            result = new InputSource(SynapseConfigUtils.resolveRelativeURI(baseUri, schemaLocation));
        }
        return result;
    }
}
