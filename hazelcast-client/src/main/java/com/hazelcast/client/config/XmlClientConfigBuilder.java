/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.client.config;

import com.hazelcast.client.util.RandomLB;
import com.hazelcast.client.util.RoundRobinLB;
import com.hazelcast.config.*;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.security.UsernamePasswordCredentials;
import com.hazelcast.util.ExceptionUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class XmlClientConfigBuilder extends AbstractXmlConfigHelper {

    private final static ILogger logger = Logger.getLogger(XmlClientConfigBuilder.class);

    private ClientConfig clientConfig;
    private InputStream in;

    public XmlClientConfigBuilder(String resource) throws IOException {
        URL url = ConfigLoader.locateConfig(resource);
        if (url == null) {
            throw new IllegalArgumentException("Could not load " + resource);
        }
        this.in = url.openStream();
    }

    public XmlClientConfigBuilder(File file) throws IOException {
        if (file == null) {
            throw new NullPointerException("File is null!");
        }
        in = new FileInputStream(file);
    }

    public XmlClientConfigBuilder(URL url) throws IOException {
        if (url == null) {
            throw new NullPointerException("URL is null!");
        }
        in = url.openStream();
    }

    public XmlClientConfigBuilder(InputStream in) {
        this.in = in;
    }

    public XmlClientConfigBuilder() {
        String configFile = System.getProperty("hazelcast.client.config");
        try {
            File configurationFile = null;
            if (configFile != null) {
                configurationFile = new File(configFile);
                logger.info("Using configuration file at " + configurationFile.getAbsolutePath());
                if (!configurationFile.exists()) {
                    String msg = "Config file at '" + configurationFile.getAbsolutePath() + "' doesn't exist.";
                    msg += "\nHazelcast will try to use the hazelcast-client.xml config file in the working directory.";
                    logger.warning(msg);
                    configurationFile = null;
                }
            }
            if (configurationFile == null) {
                configFile = "hazelcast-client-default.xml";
                configurationFile = new File("hazelcast-client-default.xml");
                if (!configurationFile.exists()) {
                    configurationFile = null;
                }
            }
            URL configurationUrl;
            if (configurationFile != null) {
                logger.info("Using configuration file at " + configurationFile.getAbsolutePath());
                try {
                    in = new FileInputStream(configurationFile);
                    configurationUrl = configurationFile.toURI().toURL();
                } catch (final Exception e) {
                    String msg = "Having problem reading config file at '" + configFile + "'.";
                    msg += "\nException message: " + e.getMessage();
                    msg += "\nHazelcast will try to use the hazelcast-client.xml config file in classpath.";
                    logger.warning(msg);
                    in = null;
                }
            }
            if (in == null) {
                logger.info("Looking for hazelcast-client.xml config file in classpath.");
                configurationUrl = Config.class.getClassLoader().getResource("hazelcast-client-default.xml");
                if (configurationUrl == null) {
                    throw new IllegalStateException("Cannot find hazelcast-client.xml in classpath, giving up.");
                }
                logger.info( "Using configuration file " + configurationUrl.getFile() + " in the classpath.");
                in = configurationUrl.openStream();
                if (in == null) {
                    throw new IllegalStateException("Cannot read configuration file, giving up.");
                }
            }
        } catch (final Throwable e) {
            logger.severe("Error while creating configuration:" + e.getMessage(), e);
        }
    }

    public ClientConfig build() {
        return build(Thread.currentThread().getContextClassLoader());
    }

    public ClientConfig build(ClassLoader classLoader) {
        final ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClassLoader(classLoader);
        try {
            parse(clientConfig);
            return clientConfig;
        } catch (Exception e) {
            throw ExceptionUtil.rethrow(e);
        }
    }

    private void parse(ClientConfig clientConfig) throws Exception {
        this.clientConfig = clientConfig;
        final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc;
        try {
            doc = builder.parse(in);
        } catch (final Exception e) {
            throw new IllegalStateException("Could not parse configuration file, giving up.");
        }
        Element element = doc.getDocumentElement();
        try {
            element.getTextContent();
        } catch (final Throwable e) {
            domLevel3 = false;
        }
        handleConfig(element);
    }

    private void handleConfig(final Element docElement) throws Exception {
        for (Node node : new IterableNodeList(docElement.getChildNodes())) {
            final String nodeName = cleanNodeName(node.getNodeName());
            if ("security".equals(nodeName)) {
                handleSecurity(node);
            } else if ("proxy-factories".equals(nodeName)) {
                handleProxyFactories(node);
            } else if ("serialization".equals(nodeName)) {
                handleSerialization(node);
            } else if ("group".equals(nodeName)) {
                handleGroup(node);
            } else if ("listeners".equals(nodeName)) {
                handleListeners(node);
            } else if ("network".equals(nodeName)) {
                handleNetwork(node);
            } else if ("load-balancer".equals(nodeName)) {
                handleLoadBalancer(node);
            } else if ("near-cache".equals(nodeName)) {
                handleNearCache(node);
            }
        }
    }

    private void handleNearCache(Node node){
        final String name = getAttribute(node, "name");
        final NearCacheConfig nearCacheConfig = new NearCacheConfig();
        for (Node child : new IterableNodeList(node.getChildNodes())) {
            final String nodeName = cleanNodeName(child);
            if ("max-size".equals(nodeName)) {
                nearCacheConfig.setMaxSize(Integer.parseInt(getTextContent(child)));
            } else if ("time-to-live-seconds".equals(nodeName)){
                nearCacheConfig.setTimeToLiveSeconds(Integer.parseInt(getTextContent(child)));
            } else if ("max-idle-seconds".equals(nodeName)){
                nearCacheConfig.setMaxIdleSeconds(Integer.parseInt(getTextContent(child)));
            } else if ("eviction-policy".equals(nodeName)){
                nearCacheConfig.setEvictionPolicy(getTextContent(child));
            } else if ("in-memory-format".equals(nodeName)){
                nearCacheConfig.setInMemoryFormat(InMemoryFormat.valueOf(getTextContent(child)));
            } else if ("invalidate-on-change".equals(nodeName)){
                nearCacheConfig.setInvalidateOnChange(Boolean.parseBoolean(getTextContent(child)));
            } else if ("cache-local-entries".equals(nodeName)){
                nearCacheConfig.setCacheLocalEntries(Boolean.parseBoolean(getTextContent(child)));
            }
        }
        clientConfig.addNearCacheConfig(name, nearCacheConfig);
    }

    private void handleLoadBalancer(Node node) {
        final String type = getAttribute(node, "type");
        if ("random".equals(type)) {
            clientConfig.setLoadBalancer(new RandomLB());
        } else if ("round-robin".equals(type)) {
            clientConfig.setLoadBalancer(new RoundRobinLB());
        }
    }

    private void handleNetwork(Node node) {
        for (Node child : new IterableNodeList(node.getChildNodes())) {
            final String nodeName = cleanNodeName(child);
            if ("cluster-members".equals(nodeName)) {
                handleClusterMembers(child);
            } else if ("smart-routing".equals(nodeName)) {
                clientConfig.setSmartRouting(Boolean.parseBoolean(getTextContent(child)));
            } else if ("redo-operation".equals(nodeName)) {
                clientConfig.setRedoOperation(Boolean.parseBoolean(getTextContent(child)));
            } else if ("connection-pool-size".equals(nodeName)) {
                clientConfig.setConnectionPoolSize(Integer.parseInt(getTextContent(child)));
            } else if ("connection-timeout".equals(nodeName)) {
                clientConfig.setConnectionTimeout(Integer.parseInt(getTextContent(child)));
            } else if ("connection-attempt-period".equals(nodeName)) {
                clientConfig.setConnectionAttemptPeriod(Integer.parseInt(getTextContent(child)));
            } else if ("connection-attempt-limit".equals(nodeName)) {
                clientConfig.setConnectionAttemptLimit(Integer.parseInt(getTextContent(child)));
            } else if ("socket-options".equals(nodeName)) {
                handleSocketOptions(child);
            }  else if ("socket-interceptor".equals(nodeName)) {
                handleSocketInterceptorConfig(node);
            }
        }
    }

    private void handleSocketOptions(Node node) {
        SocketOptions socketOptions = clientConfig.getSocketOptions();
        for (Node child : new IterableNodeList(node.getChildNodes())) {
            final String nodeName = cleanNodeName(child);
            if ("tcp-no-delay".equals(nodeName)) {
                socketOptions.setTcpNoDelay(Boolean.parseBoolean(getTextContent(child)));
            } else if ("keep-alive".equals(nodeName)) {
                socketOptions.setKeepAlive(Boolean.parseBoolean(getTextContent(child)));
            } else if ("reuse-address".equals(nodeName)) {
                socketOptions.setReuseAddress(Boolean.parseBoolean(getTextContent(child)));
            } else if ("linger-seconds".equals(nodeName)) {
                socketOptions.setLingerSeconds(Integer.parseInt(getTextContent(child)));
            } else if ("timeout".equals(nodeName)) {
                socketOptions.setTimeout(Integer.parseInt(getTextContent(child)));
            } else if ("buffer-size".equals(nodeName)) {
                socketOptions.setBufferSize(Integer.parseInt(getTextContent(child)));
            }
        }
    }

    private void handleClusterMembers(Node node) {
        for (Node child : new IterableNodeList(node.getChildNodes())) {
            if ("address".equals(cleanNodeName(child))) {
                clientConfig.addAddress(getTextContent(child));
            }
        }
    }

    private void handleListeners(Node node) throws Exception {
        for (Node child : new IterableNodeList(node.getChildNodes())) {
            if ("listener".equals(cleanNodeName(child))) {
                String className = getTextContent(child);
                clientConfig.addListenerConfig(new ListenerConfig(className));
            }
        }
    }

    private void handleGroup(Node node) {
        for (org.w3c.dom.Node n : new IterableNodeList(node.getChildNodes())) {
            final String value = getTextContent(n).trim();
            final String nodeName = cleanNodeName(n.getNodeName());
            if ("name".equals(nodeName)) {
                clientConfig.getGroupConfig().setName(value);
            } else if ("password".equals(nodeName)) {
                clientConfig.getGroupConfig().setPassword(value);
            }
        }
    }

    private void handleSerialization(Node node) {
        SerializationConfig serializationConfig = parseSerialization(node);
        clientConfig.setSerializationConfig(serializationConfig);
    }


    private void handleProxyFactories(Node node) throws Exception {
        for (Node child : new IterableNodeList(node.getChildNodes())) {
            final String nodeName = cleanNodeName(child.getNodeName());
            if ("proxy-factory".equals(nodeName)) {
                handleProxyFactory(child);
            }
        }
    }

    private void handleProxyFactory(Node node) throws Exception {
        final String service = getAttribute(node, "service");
        final String className = getAttribute(node, "class-name");

        final ProxyFactoryConfig proxyFactoryConfig = new ProxyFactoryConfig(className, service);
        clientConfig.addProxyFactoryConfig(proxyFactoryConfig);
    }

    private void handleSocketInterceptorConfig(final org.w3c.dom.Node node) {
        SocketInterceptorConfig socketInterceptorConfig = parseSocketInterceptorConfig(node);
        clientConfig.setSocketInterceptorConfig(socketInterceptorConfig);
    }

    private void handleSecurity(Node node) throws Exception {
        for (Node child : new IterableNodeList(node.getChildNodes())) {
            final String nodeName = cleanNodeName(child.getNodeName());
            if ("login-credentials".equals(nodeName)) {
                handleLoginCredentials(child);
            }
        }
    }

    private void handleLoginCredentials(Node node) {
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials();
        for (Node child : new IterableNodeList(node.getChildNodes())) {
            final String nodeName = cleanNodeName(child.getNodeName());
            if ("username".equals(nodeName)) {
                credentials.setUsername(getTextContent(child));
            } else if ("password".equals(nodeName)) {
                credentials.setPassword(getTextContent(child));
            }
        }
        clientConfig.setCredentials(credentials);
    }

}
