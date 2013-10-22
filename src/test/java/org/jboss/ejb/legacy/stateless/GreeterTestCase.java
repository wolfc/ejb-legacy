/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.ejb.legacy.stateless;

import org.jboss.aop.Advisor;
import org.jboss.aop.AspectManager;
import org.jboss.aop.AspectXmlLoader;
import org.jboss.aop.ClassAdvisor;
import org.jboss.aspects.remoting.AOPRemotingInvocationHandler;
import org.jboss.ejb3.common.metadata.MetadataUtil;
import org.jboss.ejb3.common.registrar.spi.Ejb3Registrar;
import org.jboss.ejb3.common.registrar.spi.Ejb3RegistrarLocator;
import org.jboss.ejb3.proxy.impl.jndiregistrar.JndiStatelessSessionRegistrar;
import org.jboss.ejb3.proxy.impl.objectfactory.session.stateless.StatelessSessionProxyObjectFactory;
import org.jboss.metadata.ejb.jboss.JBossEnterpriseBeansMetaData;
import org.jboss.metadata.ejb.jboss.JBossMetaData;
import org.jboss.metadata.ejb.jboss.JBossSessionBeanMetaData;
import org.jboss.metadata.ejb.spec.BusinessRemotesMetaData;
import org.jboss.remoting.ServerConfiguration;
import org.jboss.remoting.transport.Connector;
import org.jnp.server.SingletonNamingServer;
import org.junit.Test;

import javax.naming.InitialContext;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class GreeterTestCase {
    private static JBossSessionBeanMetaData createMetaData() {
        final JBossMetaData jarMetaData = new JBossMetaData();
        jarMetaData.setEjbVersion("3.0");
        final JBossEnterpriseBeansMetaData enterpriseBeansMetaData = new JBossEnterpriseBeansMetaData();
        jarMetaData.setEnterpriseBeans(enterpriseBeansMetaData);
        enterpriseBeansMetaData.setEjbJarMetaData(jarMetaData);
        final JBossSessionBeanMetaData smd = new JBossSessionBeanMetaData();
        smd.setEnterpriseBeansMetaData(enterpriseBeansMetaData);
        smd.setEjbName("GreeterBean");
        enterpriseBeansMetaData.add(smd);
        final BusinessRemotesMetaData businessRemotes = new BusinessRemotesMetaData();
        businessRemotes.add(GreeterRemote.class.getName());
        smd.setBusinessRemotes(businessRemotes);
//        // TODO: normally this is resolved through the JNDI decorator
//        smd.setJndiName("GreeterBean");
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        MetadataUtil.decorateEjbsWithJndiPolicy(jarMetaData, cl);
        return (JBossSessionBeanMetaData) jarMetaData.getEnterpriseBean(smd.getName());
    }

    private static void createRemotingConnector() throws Exception {
        final ServerConfiguration serverConfiguration = new ServerConfiguration();
        final Map<String, String> invocationHandlers = new HashMap<String, String>();
        invocationHandlers.put("AOP", AOPRemotingInvocationHandler.class.getName());
        serverConfiguration.setInvocationHandlers(invocationHandlers);
        final Connector connector = new Connector("socket://0.0.0.0:4873");
        connector.setServerConfiguration(serverConfiguration);
        Ejb3RegistrarLocator.locateRegistrar().bind("org.jboss.ejb3.RemotingConnector", connector);
        connector.start();
    }

    @Test
    public void testHello() throws Exception {
        final Ejb3Registrar ejb3Registrar = new InMemoryEjb3Registrar();
        Ejb3RegistrarLocator.bindRegistrar(ejb3Registrar);
        createRemotingConnector();

        // AOP
        final URL url = Thread.currentThread().getContextClassLoader().getResource("ejb3-interceptors-aop.xml");
        AspectXmlLoader.deployXML(url);

        final SingletonNamingServer namingServer = new SingletonNamingServer();
        final InitialContext context = new InitialContext();
        final JndiStatelessSessionRegistrar registrar = new JndiStatelessSessionRegistrar(StatelessSessionProxyObjectFactory.class.getName());
        final JBossSessionBeanMetaData smd = createMetaData();
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final String containerName = smd.getName();
        final String containerGuid = null;
        final AspectManager aspectManager = AspectManager.instance(cl);
        // TODO: probably ClassAdvisor won't do
        final Advisor advisor = new ClassAdvisor(Object.class, aspectManager);
        registrar.bindEjb(context, smd, cl, containerName, containerGuid, advisor);

        final GreeterRemote greeter = (GreeterRemote) context.lookup("GreeterBean/remote");
        final String result = greeter.sayHi("test");
        assertEquals("Hi test", result);
    }
}
