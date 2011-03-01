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

package org.apache.synapse.mediators;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.util.xpath.SynapseXPath;

/**
 * Represents a Value
 * Handling both static and dynamic(Xpath) keys.
 * User can give Xpath expression as a key and derive
 * real key based on message context
 */
public class Value {
    private static final Log log = LogFactory.getLog(Value.class);

    /**
     * Name of the value attribute
     */
    private String name = null;

    /**
     * The static key value 
     */
    private String keyValue = null;
    /**
     * the dynamic key
     */
    private SynapseXPath expression = null;

    /**
     * Create a key instance using a static key
     *
     * @param staticKey static key
     */
    public Value(String staticKey) {
        this.keyValue = staticKey;
    }

    /**
     * Create a key instance using a dynamic key (Xpath Expression)
     *
     * @param expression SynapseXpath for dynamic key
     */
    public Value(SynapseXPath expression) {
        this.expression = expression;
    }

    /**
     * Retrieving static key
     *
     * @return static key
     */
    public String getKeyValue() {
        return keyValue;
    }

    /**
     * Retrieving dynamic key
     *
     * @return SynapseXpath
     */
    public SynapseXPath getExpression() {
        return expression;
    }

    /**
     * Evaluating key based on message context
     * used when key is a xpath expression
     *
     * @param synCtx message context
     * @return string value of evaluated key
     */
    public String evaluateValue(MessageContext synCtx) {
        if (keyValue != null) {
            //if static kry: return static key
            return keyValue;
        } else if (expression != null) {
            //if dynamic key return evaluated value
            return expression.stringValueOf(synCtx);
        } else {
            handleException("Can not evaluate the key: " +
                            "key should be static or dynamic key");
            return null;
        }

    }

    /**
     * Get the name of the value attribute
     *
     * @return name of the value attribute
     */
    public String getName() {
        return name;
    }

    /**
     * Handle exceptions
     *
     * @param msg error message
     */
    private void handleException(String msg) {
        log.error(msg);
        throw new SynapseException(msg);
    }

    @Override
    public String toString() {
        return "Value {" +
                "name ='" + name + '\'' +
                (keyValue != null ? ", keyValue ='" + keyValue + '\'' : "") +
                (expression != null ? ", expression =" + expression : "") +
                '}';
    }
}


