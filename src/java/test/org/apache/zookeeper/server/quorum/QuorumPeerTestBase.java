/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * 
 */
package org.apache.zookeeper.server.quorum;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZKTestCase;
import org.apache.zookeeper.test.ClientBase;
import org.apache.zookeeper.test.QuorumBase;

/**
 * Has some common functionality for tests that work with QuorumPeers. Override
 * process(WatchedEvent) to implement the Watcher interface
 */
public class QuorumPeerTestBase extends ZKTestCase implements Watcher {
    protected static final Logger LOG = LoggerFactory
            .getLogger(QuorumPeerTestBase.class);

    public void process(WatchedEvent event) {
        // ignore for this test
    }

    public static class TestQPMain extends QuorumPeerMain {
        public void shutdown() {
            // ensure it closes - in particular wait for thread to exit
            if (quorumPeer != null) {
                QuorumBase.shutdown(quorumPeer);
            }
        }
    }
    
    public static class MainThreadReconfigRecovery extends MainThread {
       final File nextDynamicConfigFile;
       
       public MainThreadReconfigRecovery(int myid, int clientPort,
               String currentQuorumCfgSection, String nextQuorumCfgSection) 
                       throws IOException {
           super(myid, clientPort, currentQuorumCfgSection);
           nextDynamicConfigFile = new File(tmpDir, "zoo.dynamic.next");
           FileWriter fwriter = new FileWriter(nextDynamicConfigFile);
            fwriter.write(nextQuorumCfgSection + "\n");
            fwriter.flush();
            fwriter.close();
       }               
    }
    
    public static class MainThread implements Runnable {
        final File confFile;
        final File dynamicConfigFile;
        final File tmpDir;
        
        volatile TestQPMain main;

        public MainThread(int myid, int clientPort, String quorumCfgSection)
                throws IOException {
            tmpDir = ClientBase.createTmpDir();
            LOG.info("id = " + myid + " tmpDir = " + tmpDir + " clientPort = "
                    + clientPort);

            File dataDir = new File(tmpDir, "data");
            if (!dataDir.mkdir()) {
                throw new IOException("Unable to mkdir " + dataDir);
            }

            confFile = new File(tmpDir, "zoo.cfg");
            dynamicConfigFile = new File(tmpDir, "zoo.dynamic");

            FileWriter fwriter = new FileWriter(confFile);
            fwriter.write("tickTime=4000\n");
            fwriter.write("initLimit=10\n");
            fwriter.write("syncLimit=5\n");

            // Convert windows path to UNIX to avoid problems with "\"
            String dir = dataDir.toString();
            String dynamicConfigFilename = dynamicConfigFile.toString();
            String osname = java.lang.System.getProperty("os.name");
            if (osname.toLowerCase().contains("windows")) {
                dir = dir.replace('\\', '/');
                dynamicConfigFilename = dynamicConfigFilename
                        .replace('\\', '/');
            }
            fwriter.write("dataDir=" + dir + "\n");

            fwriter.write("clientPort=" + clientPort + "\n");

            fwriter.write("dynamicConfigFile=" + dynamicConfigFilename + "\n");

            fwriter.flush();
            fwriter.close();

            fwriter = new FileWriter(dynamicConfigFile);
            fwriter.write(quorumCfgSection + "\n");
            fwriter.flush();
            fwriter.close();

            File myidFile = new File(dataDir, "myid");
            fwriter = new FileWriter(myidFile);
            fwriter.write(Integer.toString(myid));
            fwriter.flush();
            fwriter.close();
        }
        
        Thread currentThread;

        synchronized public void start() {
            main = new TestQPMain();
            currentThread = new Thread(this);
            currentThread.start();
        }

        public void run() {
            String args[] = new String[1];
            args[0] = confFile.toString();
            try {
                main.initializeAndRun(args);
            } catch (Exception e) {
                // test will still fail even though we just log/ignore
                LOG.error("unexpected exception in run", e);
            } finally {
                currentThread = null;
            }
        }

        public void shutdown() throws InterruptedException {
            Thread t = currentThread;
            if (t != null && t.isAlive()) {
                main.shutdown();
                t.join(500);
            }
        }

        public void join(long timeout) throws InterruptedException {
            Thread t = currentThread;
            if (t != null) {
                t.join(timeout);
            }
        }

        public boolean isAlive() {
            Thread t = currentThread;
            return t != null && t.isAlive();
        }

        public void clean() {
            ClientBase.recursiveDelete(main.quorumPeer.getTxnFactory()
                    .getDataDir());
        }

        public boolean isQuorumPeerRunning() {
            return main.quorumPeer != null;
        }
    }
}
