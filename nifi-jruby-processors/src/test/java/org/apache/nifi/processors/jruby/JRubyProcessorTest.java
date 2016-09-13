/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.processors.jruby;

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.junit.Assert.assertThat;


public class JRubyProcessorTest {

    private static final Logger log = Logger.getLogger(JRubyProcessorTest.class.getName());

    private TestRunner testRunner;

    @Before
    public void init() {
        testRunner = TestRunners.newTestRunner(new JRubyProcessor());
        testRunner.setValidateExpressionUsage(false);
    }

    @Test
    public void testProcessor() {
      
    }

    @Test
    public void testScriptBody() {
        testRunner.setProperty(JRubyProcessor.SCRIPT_BODY, "puts 'foo'");
        testRunner.assertValid();
    }

    @Test
    public void testHelloWorldScript() {

        testRunner.setProperty(JRubyProcessor.SCRIPT_FILE, rubyFile("/test_hello_world.rb"));

        testRunner.assertValid();
        testRunner.enqueue("test content".getBytes(StandardCharsets.UTF_8));
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred("success", 1);
        final List<MockFlowFile> result = testRunner.getFlowFilesForRelationship("success");
        result.get(0).assertAttributeEquals("from-content", "Hello world");
    }

    @Test
    public void testLineNumbersScript() {

        testRunner.setProperty(JRubyProcessor.SCRIPT_FILE, rubyFile("/test_line_numbers.rb"));
        testRunner.setProperty(JRubyProcessor.SHOW_LINE_NUMBERS, "true");

        testRunner.assertValid();
        testRunner.enqueue("test content".getBytes(StandardCharsets.UTF_8));
        try {
            testRunner.run();
        } catch(AssertionError e) {
            assertThat(e.getMessage(), endsWith("(NameError) undefined local variable or method `x' for main:Object"));
        }
    }

    @Test
    public void testInputTransformation() {

        testRunner.setProperty(JRubyProcessor.SCRIPT_FILE, rubyFile("/test_input_transformation.rb"));
        testRunner.setProperty(JRubyProcessor.SHOW_LINE_NUMBERS, "true");

        testRunner.assertValid();
        testRunner.enqueue("name=foo;value=bar".getBytes(StandardCharsets.UTF_8));

        testRunner.run();

        testRunner.assertAllFlowFilesTransferred("success", 1);
        final List<MockFlowFile> result = testRunner.getFlowFilesForRelationship("success");
        MockFlowFile f = result.get(0);

        Assert.assertEquals("[{\"name\":\"foo\"},{\"value\":\"bar\"}]", new String(f.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testLocalGems() {

        testRunner.setProperty(JRubyProcessor.SCRIPT_FILE, rubyFile("/test_local_gems.rb"));
        testRunner.setProperty(JRubyProcessor.GEM_PATHS, rubyFile("/gems"));

        testRunner.assertValid();
        testRunner.enqueue("test content".getBytes(StandardCharsets.UTF_8));

        testRunner.run();

        testRunner.assertAllFlowFilesTransferred("success", 1);
        final List<MockFlowFile> result = testRunner.getFlowFilesForRelationship("success");
        result.get(0).assertAttributeEquals("from-content", "{\"somearr\":[{\"name\":\"a\"},{\"name\":\"b\"}]}");
    }


    private String rubyFile(String fileName) {
        return this.getClass().getResource(fileName).getPath();
    }
}
