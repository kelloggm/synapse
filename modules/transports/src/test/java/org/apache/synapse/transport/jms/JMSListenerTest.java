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

package org.apache.synapse.transport.jms;

import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.axis2.context.MessageContext;
import org.apache.synapse.transport.testkit.listener.AxisAsyncMessageSender;
import org.apache.synapse.transport.testkit.listener.AxisRequestResponseMessageSender;
import org.apache.synapse.transport.testkit.listener.ContentTypeMode;
import org.apache.synapse.transport.testkit.listener.ListenerTestSuite;
import org.apache.synapse.transport.testkit.listener.MessageTestData;
import org.apache.synapse.transport.testkit.listener.XMLAsyncMessageSender;

public class JMSListenerTest extends TestCase {
    public static TestSuite suite() {
        ListenerTestSuite suite = new ListenerTestSuite();
        JMSListenerSetup setup = new QpidTestSetup();
        JMSBytesMessageSender bytesMessageSender = new JMSBytesMessageSender();
        JMSTextMessageSender textMessageSender = new JMSTextMessageSender();
        List<XMLAsyncMessageSender<? super JMSAsyncChannel>> senders = new LinkedList<XMLAsyncMessageSender<? super JMSAsyncChannel>>();
        senders.add(bytesMessageSender);
        senders.add(textMessageSender);
        senders.add(new AxisAsyncMessageSender());
        suite.addPOXTests(new JMSRequestResponseChannel(setup, JMSConstants.DESTINATION_TYPE_QUEUE, JMSConstants.DESTINATION_TYPE_QUEUE), new AxisRequestResponseMessageSender(), ContentTypeMode.TRANSPORT);
        for (String destinationType : new String[] { JMSConstants.DESTINATION_TYPE_QUEUE, JMSConstants.DESTINATION_TYPE_TOPIC }) {
            JMSAsyncChannel channel = new JMSAsyncChannel(setup, destinationType);
            for (ContentTypeMode contentTypeMode : ContentTypeMode.values()) {
                for (XMLAsyncMessageSender<? super JMSAsyncChannel> sender : senders) {
                    if (contentTypeMode == ContentTypeMode.TRANSPORT) {
                        suite.addSOAPTests(channel, sender, contentTypeMode);
                        suite.addPOXTests(channel, sender, contentTypeMode);
                    } else {
                        // If no content type header is used, SwA can't be used and the JMS transport
                        // always uses the default charset encoding
                        suite.addSOAP11Test(channel, sender, contentTypeMode, new MessageTestData(null, ListenerTestSuite.testString,
                                MessageContext.DEFAULT_CHAR_SET_ENCODING));
                        suite.addSOAP12Test(channel, sender, contentTypeMode, new MessageTestData(null, ListenerTestSuite.testString,
                                MessageContext.DEFAULT_CHAR_SET_ENCODING));
                        suite.addPOXTest(channel, sender, contentTypeMode, new MessageTestData(null, ListenerTestSuite.testString,
                                MessageContext.DEFAULT_CHAR_SET_ENCODING));
                    }
                }
                if (contentTypeMode == ContentTypeMode.TRANSPORT) {
                    suite.addSwATests(channel, bytesMessageSender);
                }
                // TODO: these tests are temporarily disabled because of SYNAPSE-304
                // addTextPlainTests(strategy, suite);
                suite.addBinaryTest(channel, bytesMessageSender, contentTypeMode);
            }
        }
        return suite;
    }
}
