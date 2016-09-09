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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.Validator;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.*;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.jruby.embed.LocalVariableBehavior;
import org.jruby.embed.ScriptingContainer;
import org.jruby.javasupport.JavaEmbedUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Tags({"script", "execute", "jruby"})
@CapabilityDescription("Execute a JRuby script using a flow file and a process session.")
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute="", description="")})
@WritesAttributes({@WritesAttribute(attribute="", description="")})
public class JRubyProcessor extends AbstractSessionFactoryProcessor {


    protected BlockingQueue<ScriptingContainer> engineQ = null;
    private Boolean showLineNumbers = false;

    private List<PropertyDescriptor> descriptors;
    private Set<Relationship> relationships;

    private String scriptToRun = null;

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("FlowFiles that were successfully processed")
            .build();

    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("FlowFiles that failed to be processed")
            .build();

    public static final PropertyDescriptor SCRIPT_FILE = new PropertyDescriptor.Builder()
            .name("Script File")
            .required(false)
            .description("Path to script file to execute. Only one of Script File or Script Body may be used")
            .addValidator(new StandardValidators.FileExistsValidator(true))
            .expressionLanguageSupported(true)
            .build();

    public static final PropertyDescriptor SCRIPT_BODY = new PropertyDescriptor.Builder()
            .name("Script Body")
            .required(false)
            .description("Body of script to execute. Only one of Script File or Script Body may be used")
            .addValidator(Validator.VALID)
            .expressionLanguageSupported(false)
            .build();

    public static final PropertyDescriptor JRUBY_HOME = new PropertyDescriptor.Builder()
            .name("Optional JRuby home")
            .description("Point to an external JRuby home")
            .required(false)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor SHOW_LINE_NUMBERS = new PropertyDescriptor.Builder()
            .name("Show line numbers")
            .description("If there is an error, show the line number for the error")
            .required(true)
            .allowableValues("true", "false")
            .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
            .defaultValue("false")
            .expressionLanguageSupported(false)
            .build();

    public static final PropertyDescriptor GEM_PATHS = new PropertyDescriptor.Builder()
            .name("Additional gem paths for ruby gems")
            .description("Colon-separated list of paths to files and/or directories which contain libraries required by the script.")
            .required(false)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();


    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();

        descriptors.add(SCRIPT_FILE);
        descriptors.add(SCRIPT_BODY);
        descriptors.add(JRUBY_HOME);
        descriptors.add(SHOW_LINE_NUMBERS);
        descriptors.add(GEM_PATHS);
        this.descriptors = Collections.unmodifiableList(descriptors);

        final Set<Relationship> relationships = new HashSet<Relationship>();
        relationships.add(REL_FAILURE);
        relationships.add(REL_SUCCESS);
        this.relationships = Collections.unmodifiableSet(relationships);

    }

    @Override
    public Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }


    @OnScheduled
    public void setup(final ProcessContext context) {

        String scriptBody = context.getProperty(SCRIPT_BODY).getValue();
        String scriptFile = context.getProperty(SCRIPT_FILE).getValue();

        // Show line numbers
        showLineNumbers = context.getProperty(SHOW_LINE_NUMBERS).asBoolean();

        scriptToRun = scriptBody;

        try {
            if (scriptToRun == null && scriptFile != null) {
                try (final FileInputStream scriptStream = new FileInputStream(scriptFile)) {
                    scriptToRun = IOUtils.toString(scriptStream);
                }
            }
        } catch (IOException ioe) {
            throw new ProcessException(ioe);
        }

        int maxTasks = context.getMaxConcurrentTasks();
        setupScriptingContainers(context, maxTasks);
    }

    protected void setupScriptingContainers(final ProcessContext context, int numberOfContainers) {
        engineQ = new LinkedBlockingQueue<>(numberOfContainers);
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            ComponentLog log = getLogger();

            for (int i = 0; i < numberOfContainers; i++) {
                ScriptingContainer scriptingContainer = setupScriptingContainer(context);
                if (!engineQ.offer(scriptingContainer)) {
                    log.error("Error adding JRuby script container");
                }

            }
        } finally {
            // Restore original context class loader
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    protected ScriptingContainer setupScriptingContainer(final ProcessContext context) {
        ScriptingContainer scriptingContainer = new ScriptingContainer(LocalVariableBehavior.PERSISTENT);

        Map<String, String> env = new HashMap<>(scriptingContainer.getEnvironment());

        // Change JRUBY_HOME location
        String jrubyHome = context.getProperty(JRUBY_HOME).getValue();
        if (!StringUtils.isEmpty(jrubyHome)) {
            scriptingContainer.setHomeDirectory(jrubyHome);
        }

        // Add additional gem paths
        String gemPath = context.getProperty(GEM_PATHS).getValue();
        if (!StringUtils.isEmpty(gemPath)) {
            String oldGemPath = env.get("GEM_PATH");
            String newGemPath;
            if (oldGemPath != null) {
                newGemPath = oldGemPath + ":" + gemPath;
            } else {
                newGemPath = gemPath;
            }
            System.out.println(newGemPath);
            env.put("GEM_PATH", newGemPath);
        }

        scriptingContainer.setEnvironment(env);

        return scriptingContainer;
    }

    @Override
    public void onTrigger(ProcessContext processContext, ProcessSessionFactory processSessionFactory) throws ProcessException {
        ProcessSession session = processSessionFactory.createSession();
        ComponentLog log = getLogger();

        ScriptingContainer scriptingContainer = engineQ.poll();

        try {

            try {
                scriptingContainer.put("session", session);
                scriptingContainer.put("context", processContext);
                scriptingContainer.put("log", log);
                scriptingContainer.put("REL_SUCCESS", REL_SUCCESS);
                scriptingContainer.put("REL_FAILURE", REL_FAILURE);

                if (showLineNumbers) {
                    JavaEmbedUtils.EvalUnit unit = scriptingContainer.parse(scriptToRun, 1);
                    unit.run();
                } else {
                    scriptingContainer.runScriptlet(scriptToRun);
                }

                session.commit();
            } catch (Exception e) {
                throw new ProcessException(e);
            }
        } catch (final Throwable t) {
            getLogger().error("{} failed to process due to {}; rolling back session", new Object[]{this, t});
            session.rollback(true);
            throw t;
        } finally {
            engineQ.offer(scriptingContainer);
        }

    }

    @OnStopped
    public void stop() {
        if (engineQ != null) {
            engineQ.clear();
        }
    }
}
