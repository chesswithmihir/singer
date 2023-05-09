/**
 * Copyright 2019 Pinterest, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pinterest.singer.kubernetes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.ClientProtocolException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.pinterest.singer.thrift.configuration.KubeConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 */
@SuppressWarnings("restriction")
public class TestKubeService {

    private static HttpServer server;

    @BeforeClass
    public static void beforeClass() throws IOException {
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(10255), 0);
        server.start();
    }

    @AfterClass
    public static void afterClass() {
        server.stop(0);
    }

    @Before
    public void before() {
        try {
            server.removeContext("/pods");
        } catch (Exception e) {
        }
    }
    
    @After
    public void after() {
        try {
            server.removeContext("/pods");
        } catch (Exception e) {
        }
    }

    @Test
    public void testInit() throws IOException {
        // good init
        KubeConfig kubeConfig = new KubeConfig();
        kubeConfig.setPollFrequencyInSeconds(10);
        KubeService poll = new KubeService(kubeConfig);
        assertEquals(10 * 1000, poll.getPollFrequency());

        // bad init
        kubeConfig.setPollFrequencyInSeconds(7200);
        try {
            poll = new KubeService(kubeConfig);
            fail("Must throw an exception when initialized with bad poll frequency");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testGoodPodFetch()
        throws KeyManagementException, ClientProtocolException, NoSuchAlgorithmException,
               KeyStoreException, MalformedURLException, IOException {
        registerGoodResponse();

        KubeConfig kubeConfig = new KubeConfig();
        KubeService poll = new KubeService(kubeConfig);
        Set<String> fetchPodNamesFromMetadata = poll.fetchPodNamesFromMetadata();

        // check uid count is correct
        assertEquals(6, fetchPodNamesFromMetadata.size());

        List<String> podNameList = new ArrayList<>();
        podNameList.add(
            "default" + "_" + "enimanager-ppl56" + "_" + "b1a2a0c2-d3ab-11e7-a1db-0674200569b6");
        podNameList.add("default" + "_" + "zk-update-monitor-r1vlt" + "_"
            + "7d1d8b00-b532-11e7-b628-0674200569b6");
        podNameList.add(
            "default" + "_" + "tcollector-q4qx8" + "_" + "8ad2dba8-b84c-11e7-b628-0674200569b6");
        podNameList.add(
            "default" + "_" + "metrics-agent-8vm9g" + "_" + "bfc5f760-b8e1-11e7-b628-0674200569b6");
        podNameList.add("kube-system" + "_" + "kubernetes-dashboard-1835568627-hhfhj" + "_"
            + "a343643b-b936-11e7-b628-0674200569b6");
        podNameList.add(
            "kubernetes-plugin" + "_" + "test-ci-0" + "_" + "f084af12-cbe6-11e7-a1db-0674200569b6");
        for (String podName : podNameList) {
            assertTrue(fetchPodNamesFromMetadata.contains(podName));
        }
    }

    @Test
    public void testPodFetchWithTwoFormats() throws IOException {
        registerGoodResponse();

        String firstFormat = "default_enimanager-ppl56";
        String secondFormat = "default_enimanager-ppl56_b1a2a0c2-d3ab-11e7-a1db-0674200569b6";
        String podDirectory = "target/kube";
        File f = new File(podDirectory + "/" + firstFormat);
        if (!f.exists()) {
            f.mkdirs();
        }
        KubeConfig kubeConfig = new KubeConfig();
        kubeConfig.setPodLogDirectory(podDirectory);
        KubeService poll = new KubeService(kubeConfig);
        Set<String> fetchPodNamesFromMetadata = poll.fetchPodNamesFromMetadata();
        assertFalse(fetchPodNamesFromMetadata.contains(secondFormat));
        f.delete();
        f = new File(podDirectory + "/" + secondFormat);
        if (!f.exists()) {
            f.mkdirs();
        }
        fetchPodNamesFromMetadata = poll.fetchPodNamesFromMetadata();
        assertFalse(fetchPodNamesFromMetadata.contains(firstFormat));
        f.delete();
    }

    @Test
    public void testBadPodFetch() throws KeyManagementException, ClientProtocolException, NoSuchAlgorithmException,
            KeyStoreException, MalformedURLException, IOException {
        registerBadResponse();

        KubeConfig kubeConfig = new KubeConfig();
        KubeService kubeService = new KubeService(kubeConfig);
        Set<String> fetchPodIdsFromMetadata = kubeService.fetchPodNamesFromMetadata();

        assertEquals(0, fetchPodIdsFromMetadata.size());
    }

    @Test
    public void testPollService() throws InterruptedException, IOException {
        registerGoodResponse();

        KubeConfig kubeConfig = new KubeConfig();
        new File("target/kube").mkdirs();
        kubeConfig.setPodLogDirectory("target/kube");
        KubeService kubeService = new KubeService(kubeConfig);
        Thread thTest = new Thread(kubeService);
        thTest.setDaemon(true);
        thTest.start();
        Thread.sleep(1000);
        
        // no pods should be polled
        assertEquals(0, kubeService.getActivePodSet().size());

        // send interrupt to kill thread
        thTest.interrupt();
    }

//    @Test
    public void testListener() throws IOException {
        registerGoodResponse();

        Set<String> set = new ConcurrentSkipListSet<>();

        KubeConfig kubeConfig = new KubeConfig();
        KubeService poll = new KubeService(kubeConfig);
        poll.addWatcher(new PodWatcher() {

            @Override
            public void podDeleted(String podUid) {
                // TODO Auto-generated method stub

            }

            @Override
            public void podCreated(String podUid) {
                set.add(podUid);
            }
        });
        poll.updatePodNames();

        assertEquals(6, set.size());
        set.clear();
        
        poll.addWatcher(new PodWatcher() {

            @Override
            public void podDeleted(String podUid) {
                // TODO Auto-generated method stub

            }

            @Override
            public void podCreated(String podUid) {
                throw new NullPointerException("Exception for test");
            }
        });
        
        poll.updatePodNames();
        assertEquals(6, set.size());
    }

    public void registerBadResponse() {
        server.createContext("/pods", new HttpHandler() {

            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, 0);
                exchange.close();
            }
        });
    }

    public void registerBadJsonResponse() {
        server.createContext("/pods", new HttpHandler() {

            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try {
                    String response = new String(
                            Files.readAllBytes(new File("src/test/resources/pods-badresponse.json").toPath()), "utf-8");
                    exchange.getResponseHeaders().add("Content-Type", "text/html");
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length());
                    IOUtils.write(response, exchange.getResponseBody());
                    exchange.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    throw e;
                }
            }
        });
    }

    public void registerGoodResponse() {
        server.createContext("/pods", new HttpHandler() {

            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try {
                    String response = new String(
                            Files.readAllBytes(new File("src/test/resources/pods-goodresponse.json").toPath()),
                            "utf-8");
                    exchange.getResponseHeaders().add("Content-Type", "text/html");
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length());
                    IOUtils.write(response, exchange.getResponseBody());
                    exchange.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    throw e;
                }
            }
        });
    }

}
