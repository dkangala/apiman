/**
 *  Copyright 2005-2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.apiman.manager.api.micro;

import io.apiman.common.servlet.ApimanCorsFilter;
import io.apiman.common.servlet.AuthenticationFilter;
import io.apiman.common.servlet.DisableCachingFilter;
import io.apiman.common.servlet.LocaleFilter;
import io.apiman.common.servlet.RootResourceFilter;
import io.apiman.manager.api.security.impl.DefaultSecurityContextFilter;

import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Credential;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap;
import org.jboss.weld.environment.servlet.BeanManagerResourceBindingListener;
import org.jboss.weld.environment.servlet.Listener;

/**
 * This class starts up an embedded Jetty test server so that integration tests
 * can be performed.
 *
 * @author eric.wittmann@redhat.com
 */
@SuppressWarnings("nls")
public class ManagerApiMicroService {

    private Server server;

    /**
     * Constructor.
     */

    public ManagerApiMicroService() {
    }


    /**
     * Start/run the server.
     * @throws Exception when any exception occurs
     */
    public void start() throws Exception {
        long startTime = System.currentTimeMillis();

        ContextHandlerCollection handlers = new ContextHandlerCollection();
        addModulesToJetty(handlers);
        
        // Create the server.
        int serverPort = serverPort();
        System.out.println("**** Starting Server (" + getClass().getSimpleName() + ") on port: " + serverPort);
        server = new Server(serverPort);
        server.setHandler(handlers);
        server.start();
        long endTime = System.currentTimeMillis();
        System.out.println("******* Started in " + (endTime - startTime) + "ms");
    }

    /**
     * Stop the server.
     * @throws Exception when any exception occurs
     */
    public void stop() throws Exception {
        server.stop();
    }

    /**
     * @return the server port.
     */
    public int serverPort() {
        return Integer.parseInt(System.getProperty("apiman-manager.api.port", "7070"));
    }

    /**
     * Configure the web application(s).
     * @param handlers
     * @throws Exception
     */
    protected void addModulesToJetty(ContextHandlerCollection handlers) throws Exception {
    	/* *************
         * Manager API
         * ************* */
        ServletContextHandler apiManServer = new ServletContextHandler(ServletContextHandler.SESSIONS);
        addSecurityHandler(apiManServer);
        apiManServer.setContextPath("/apiman");
        apiManServer.addEventListener(new Listener());
        apiManServer.addEventListener(new BeanManagerResourceBindingListener());
        apiManServer.addEventListener(new ResteasyBootstrap());
        apiManServer.addFilter(LocaleFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        apiManServer.addFilter(ApimanCorsFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        apiManServer.addFilter(DisableCachingFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        addAuthFilter(apiManServer);
        apiManServer.addFilter(DefaultSecurityContextFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        apiManServer.addFilter(RootResourceFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        ServletHolder resteasyServlet = new ServletHolder(new HttpServletDispatcher());
        resteasyServlet.setInitParameter("javax.ws.rs.Application", ManagerApiMicroServiceApplication.class.getName());
        apiManServer.addServlet(resteasyServlet, "/*");

        apiManServer.setInitParameter("resteasy.injector.factory", "org.jboss.resteasy.cdi.CdiInjectorFactory");
        apiManServer.setInitParameter("resteasy.scan", "true");
        apiManServer.setInitParameter("resteasy.servlet.mapping.prefix", "");

        handlers.addHandler(apiManServer);
    }


    /**
     * @param apiManServer
     */
    protected void addSecurityHandler(ServletContextHandler apiManServer) {
        apiManServer.setSecurityHandler(createSecurityHandler());
    }

    /**
     * @param apiManServer
     */
    protected void addAuthFilter(ServletContextHandler apiManServer) {
        apiManServer.addFilter(AuthenticationFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
    }

    /**
     * Creates a basic auth security handler.
     */
    protected SecurityHandler createSecurityHandler() {
        HashLoginService l = new HashLoginService();
        for (User user : Users.getUsers()) {
            l.putUser(user.getId(), Credential.getCredential(user.getPassword()), user.getRolesAsArray());
        }
        l.setName("apimanrealm");

        ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName("apimanrealm");
        csh.setLoginService(l);

        return csh;
    }

    /**
     * @throws InterruptedException when interrupted
     */
    public void join() throws InterruptedException {
        server.join();
    }

}
