//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.jsr356.server;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpoint;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.websocket.api.util.WSURI;
import org.eclipse.jetty.websocket.jsr356.ClientContainer;
import org.eclipse.jetty.websocket.jsr356.JettyClientContainerProvider;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.junit.Before;
import org.junit.Test;

public class DelayedStartClientOnServerTest
{
    @ServerEndpoint("/echo")
    public static class EchoSocket
    {
        @OnMessage
        public String echo(String msg)
        {
            return msg;
        }
    }
    
    @Before
    public void stopClientContainer() throws Exception
    {
        JettyClientContainerProvider.stop();
    }
    
    /**
     * Using the Client specific techniques of JSR356, connect to the echo socket
     * and perform an echo request.
     */
    public static class ClientConnectServlet extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
        {
            // Client specific technique
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            try
            {
                URI wsURI = WSURI.toWebsocket(req.getRequestURL()).resolve("/echo");
                Session session = container.connectToServer(new Endpoint()
                {
                    @Override
                    public void onOpen(Session session, EndpointConfig config)
                    {
                        /* do nothing */
                    }
                }, wsURI);
                // don't care about the data sent, just the connect itself.
                session.getBasicRemote().sendText("Hello");
                session.close();
                resp.setContentType("text/plain");
                resp.getWriter().println("Connected to " + wsURI);
            }
            catch (Throwable t)
            {
                throw new ServletException(t);
            }
        }
    }
    
    /**
     * Using the Server specific techniques of JSR356, connect to the echo socket
     * and perform an echo request.
     */
    public static class ServerConnectServlet extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
        {
            // Server specific technique
            javax.websocket.server.ServerContainer container =
                    (javax.websocket.server.ServerContainer)
                            req.getServletContext().getAttribute("javax.websocket.server.ServerContainer");
            try
            {
                URI wsURI = WSURI.toWebsocket(req.getRequestURL()).resolve("/echo");
                Session session = container.connectToServer(new Endpoint()
                {
                    @Override
                    public void onOpen(Session session, EndpointConfig config)
                    {
                        /* do nothing */
                    }
                }, wsURI);
                // don't care about the data sent, just the connect itself.
                session.getBasicRemote().sendText("Hello");
                session.close();
                resp.setContentType("text/plain");
                resp.getWriter().println("Connected to " + wsURI);
            }
            catch (Throwable t)
            {
                throw new ServletException(t);
            }
        }
    }
    
    /**
     * Using the Client specific techniques of JSR356, configure the WebSocketContainer.
     */
    public static class ClientConfigureServlet extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
        {
            // Client specific technique
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            
            try
            {
                container.setAsyncSendTimeout(5000);
                container.setDefaultMaxTextMessageBufferSize(1000);
                resp.setContentType("text/plain");
                resp.getWriter().printf("Configured %s - %s%n", container.getClass().getName(), container);
            }
            catch (Throwable t)
            {
                throw new ServletException(t);
            }
        }
    }
    
    /**
     * Using the Server specific techniques of JSR356, configure the WebSocketContainer.
     */
    public static class ServerConfigureServlet extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
        {
            // Server specific technique
            javax.websocket.server.ServerContainer container =
                    (javax.websocket.server.ServerContainer)
                            req.getServletContext().getAttribute("javax.websocket.server.ServerContainer");
            try
            {
                container.setAsyncSendTimeout(5000);
                container.setDefaultMaxTextMessageBufferSize(1000);
                resp.setContentType("text/plain");
                resp.getWriter().printf("Configured %s - %s%n", container.getClass().getName(), container);
            }
            catch (Throwable t)
            {
                throw new ServletException(t);
            }
        }
    }
    
    @Test
    public void testNoExtraHttpClientThreads() throws Exception
    {
        Server server = new Server(0);
        ServletContextHandler contextHandler = new ServletContextHandler();
        server.setHandler(contextHandler);
        WebSocketServerContainerInitializer.configureContext(contextHandler);
        try
        {
            server.start();
            List<String> threadNames = getThreadNames();
            assertThat("Threads", threadNames, not(hasItem(containsString("SimpleContainerScope.Executor@"))));
            assertThat("Threads", threadNames, not(hasItem(containsString("HttpClient@"))));
        }
        finally
        {
            server.stop();
        }
    }
    
    @Test
    public void testHttpClientThreads_AfterClientConnectTo() throws Exception
    {
        Server server = new Server(0);
        ServletContextHandler contextHandler = new ServletContextHandler();
        server.setHandler(contextHandler);
        // Using JSR356 Client Techniques to connectToServer()
        contextHandler.addServlet(ClientConnectServlet.class, "/connect");
        javax.websocket.server.ServerContainer container = WebSocketServerContainerInitializer.configureContext(contextHandler);
        container.addEndpoint(EchoSocket.class);
        try
        {
            server.start();
            String response = GET(server.getURI().resolve("/connect"));
            assertThat("Response", response, startsWith("Connected to ws://"));
            List<String> threadNames = getThreadNames();
            assertThat("Threads", threadNames, hasItem(containsString("SimpleContainerScope.Executor@")));
            assertThat("Threads", threadNames, hasItem(containsString("HttpClient@")));
        }
        finally
        {
            server.stop();
        }
    }
    
    @Test
    public void testHttpClientThreads_AfterServerConnectTo() throws Exception
    {
        Server server = new Server(0);
        ServletContextHandler contextHandler = new ServletContextHandler();
        server.setHandler(contextHandler);
        // Using JSR356 Server Techniques to connectToServer()
        contextHandler.addServlet(ServerConnectServlet.class, "/connect");
        javax.websocket.server.ServerContainer container = WebSocketServerContainerInitializer.configureContext(contextHandler);
        container.addEndpoint(EchoSocket.class);
        try
        {
            server.start();
            String response = GET(server.getURI().resolve("/connect"));
            assertThat("Response", response, startsWith("Connected to ws://"));
            List<String> threadNames = getThreadNames();
            assertThat("Threads", threadNames, hasItem(containsString("HttpClient@")));
        }
        finally
        {
            server.stop();
        }
    }
    
    @Test
    public void testHttpClientThreads_AfterClientConfigure() throws Exception
    {
        Server server = new Server(0);
        ServletContextHandler contextHandler = new ServletContextHandler();
        server.setHandler(contextHandler);
        // Using JSR356 Client Techniques to configure WebSocketContainer
        contextHandler.addServlet(ClientConfigureServlet.class, "/configure");
        javax.websocket.server.ServerContainer container = WebSocketServerContainerInitializer.configureContext(contextHandler);
        container.addEndpoint(EchoSocket.class);
        try
        {
            server.start();
            String response = GET(server.getURI().resolve("/configure"));
            assertThat("Response", response, startsWith("Configured " + ClientContainer.class.getName()));
            List<String> threadNames = getThreadNames();
            assertThat("Threads", threadNames, not(hasItem(containsString("SimpleContainerScope.Executor@"))));
            assertThat("Threads", threadNames, not(hasItem(containsString("HttpClient@"))));
        }
        finally
        {
            server.stop();
        }
    }
    
    @Test
    public void testHttpClientThreads_AfterServerConfigure() throws Exception
    {
        Server server = new Server(0);
        ServletContextHandler contextHandler = new ServletContextHandler();
        server.setHandler(contextHandler);
        // Using JSR356 Server Techniques to configure WebSocketContainer
        contextHandler.addServlet(ServerConfigureServlet.class, "/configure");
        javax.websocket.server.ServerContainer container = WebSocketServerContainerInitializer.configureContext(contextHandler);
        container.addEndpoint(EchoSocket.class);
        try
        {
            server.start();
            String response = GET(server.getURI().resolve("/configure"));
            assertThat("Response", response, startsWith("Configured " + ServerContainer.class.getName()));
            List<String> threadNames = getThreadNames();
            assertThat("Threads", threadNames, not(hasItem(containsString("SimpleContainerScope.Executor@"))));
            assertThat("Threads", threadNames, not(hasItem(containsString("HttpClient@"))));
        }
        finally
        {
            server.stop();
        }
    }
    
    private String GET(URI destURI) throws IOException
    {
        HttpURLConnection http = (HttpURLConnection) destURI.toURL().openConnection();
        assertThat("HTTP GET (" + destURI + ") Response Code", http.getResponseCode(), is(200));
        try (InputStream in = http.getInputStream();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             StringWriter writer = new StringWriter())
        {
            IO.copy(reader, writer);
            return writer.toString();
        }
    }
    
    private List<String> getThreadNames()
    {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threads = threadMXBean.dumpAllThreads(false, false);
        List<String> ret = new ArrayList<>();
        for (ThreadInfo info : threads)
        {
            ret.add(info.getThreadName());
        }
        return ret;
    }
}
