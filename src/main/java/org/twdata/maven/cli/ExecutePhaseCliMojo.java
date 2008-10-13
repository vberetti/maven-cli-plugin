package org.twdata.maven.cli;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import jline.ConsoleReader;

import org.apache.maven.Maven;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.profiles.DefaultProfileManager;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.embed.Embedder;
import org.codehaus.plexus.util.StringUtils;

/**
 * Provides an interactive command line interface for Maven plugins, allowing
 * users to execute plugins directly.
 * 
 * @requiresDependencyResolution execute
 * @aggregator true
 * @goal execute-phase
 */
public class ExecutePhaseCliMojo extends AbstractMojo {

    private final List<String> defaultPhases = Collections
            .unmodifiableList(new ArrayList<String>() {
                {
                    add("clean");

                    add("validate");
                    add("generate-sources");
                    add("generate-resources");
                    add("test-compile");
                    add("test");
                    add("package");
                    add("integration-test");
                    add("install");
                    add("deploy");

                    add("site");
                    add("site-deploy");
                }
            });

    private final List<String> defaultProperties = Collections
            .unmodifiableList(new ArrayList<String>() {
                {
                    add("-o");
                    add("-N");
                    add("-Dmaven.test.skip=true");
                    add("-DskipTests");
                }
            });

    private final List<String> listCommands = Collections
            .unmodifiableList(new ArrayList<String>() {
                {
                    add("list");
                    add("ls");
                }
            });

    private final List<String> exitCommands = Collections
            .unmodifiableList(new ArrayList<String>() {
                {
                    add("quit");
                    add("exit");
                    add("bye");
                }
            });

    /**
     * Command aliases. Commands should be in the form GROUP_ID:ARTIFACT_ID:GOAL
     * 
     * @parameter
     */
    private Map<String, String> userAliases;

    /**
     * The Maven Project Object
     * 
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The Maven Session Object
     * 
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    protected MavenSession session;

    /**
     * The Maven PluginManager Object
     * 
     * @component
     * @required
     */
    protected PluginManager pluginManager;

    /**
     * The reactor projects.
     * 
     * @parameter expression="${reactorProjects}"
     * @readonly
     */
    protected List reactorProjects;

    protected Map<String, MavenProject> modules;

    protected Embedder embedder;
    protected Maven embeddedMaven;
    protected File userDir;

    public void execute() throws MojoExecutionException {
        modules = new HashMap<String, MavenProject>();
        for (Object reactorProject : reactorProjects) {
            modules.put(((MavenProject) reactorProject).getArtifactId(),
                    (MavenProject) reactorProject);
        }

        if (userAliases == null) {
            userAliases = new HashMap<String, String>();
        }

        initEmbeddedMaven();

        // build list of commands available for completion
        List<String> availableCommands = new ArrayList<String>();
        availableCommands.addAll(defaultPhases);
        availableCommands.addAll(userAliases.keySet());
        availableCommands.addAll(exitCommands);
        availableCommands.addAll(listCommands);
        availableCommands.addAll(modules.keySet());
        availableCommands.addAll(defaultProperties);

        getLog().info("Waiting for commands");
        try {
            ConsoleReader reader = new ConsoleReader(System.in,
                    new OutputStreamWriter(System.out));
            reader.addCompletor(new CommandsCompletor(availableCommands));
            reader.setBellEnabled(false);
            reader.setDefaultPrompt("maven2> ");
            String line;

            while ((line = readCommand(reader)) != null) {
                if (StringUtils.isEmpty(line)) {
                    continue;
                } else if (exitCommands.contains(line)) {
                    break;
                } else if (listCommands.contains(line)) {
                    getLog().info("Listing available projects: ");
                    for (Object reactorProject : reactorProjects) {
                        getLog().info(
                                "* "
                                        + ((MavenProject) reactorProject)
                                                .getArtifactId());
                    }
                } else {
                    List<CommandCall> calls = new ArrayList<CommandCall>();
                    try {
                        parseCommand(line, calls);
                    } catch (IllegalArgumentException ex) {
                        getLog().error("Invalid command: " + line);
                        continue;
                    }

                    for (CommandCall call : calls) {
                        getLog().info("Executing: " + call);
                        long start = System.currentTimeMillis();
                        executeCommand(call);
                        long now = System.currentTimeMillis();
                        getLog().info(
                                "Execution time: " + (now - start) + " ms");

                    }
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to execute cli commands",
                    e);
        }
    }

    private void initEmbeddedMaven() throws MojoExecutionException {
        try {
            embedder = new Embedder();
            embedder.start();
            embeddedMaven = (Maven) embedder.lookup(Maven.ROLE);
            userDir = new File(System.getProperty("user.dir"));
        } catch (PlexusContainerException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (ComponentLookupException e) {
            throw new MojoExecutionException(e.getMessage());
        }
    }

    /**
     * Recursively parses commands to resolve all aliases
     * 
     * @param text
     *            The text to evaluate
     * @param aliases
     *            The list of aliases available
     * @param commands
     *            The list of commands found so far
     */
    private void parseCommand(String text, List<CommandCall> commands) {
        List<String> tokens = Arrays.asList(text.split(" "));

        // resolve aliases
        int i = 0;
        while (i < tokens.size()) {
            String token = tokens.get(i);
            if (userAliases.containsKey(token)) {
                String alias = userAliases.get(token);
                List<String> aliasTokens = Arrays.asList(alias.split(" "));
                tokens.remove(i);
                tokens.addAll(i, aliasTokens);
            } else {
                i++;
            }
        }

        CommandCall currentCommandCall = null;
        for (String token : tokens) {
            if (modules.containsKey(token)) {
                currentCommandCall = addProject(commands, currentCommandCall,
                        modules.get(token));
            } else if (token.contains("*")) {
                String regexToken = token.replaceAll("\\*", ".*");
                for (String moduleName : modules.keySet()) {
                    if (Pattern.matches(regexToken, moduleName)) {
                        currentCommandCall = addProject(commands,
                                currentCommandCall, modules.get(moduleName));
                    }
                }
            } else if (token.startsWith("-")) {
                addProperty(commands, currentCommandCall, token);
            } else {
                currentCommandCall = addCommand(commands, currentCommandCall,
                        token);
            }
        }
    }

    private String readCommand(ConsoleReader reader) throws IOException {
        return reader.readLine();
    }

    private CommandCall addProject(List<CommandCall> commands,
            CommandCall currentCommandCall, MavenProject project) {
        if (currentCommandCall == null
                || !currentCommandCall.getCommands().isEmpty()) {
            currentCommandCall = new CommandCall();
            commands.add(currentCommandCall);
        }
        currentCommandCall.getProjets().add(project);
        return currentCommandCall;
    }

    private CommandCall addCommand(List<CommandCall> commands,
            CommandCall currentCommandCall, String command) {
        if (currentCommandCall == null) {
            currentCommandCall = new CommandCall();
            currentCommandCall.getProjets().add(this.project);
            commands.add(currentCommandCall);
        }
        currentCommandCall.getCommands().add(command);
        return currentCommandCall;
    }

    private CommandCall addProperty(List<CommandCall> commands,
            CommandCall currentCommandCall, String property) {
        if (currentCommandCall == null) {
            currentCommandCall = new CommandCall();
            commands.add(currentCommandCall);
        }
        property = property.substring(2);
        String[] propertyTokens = property.split("=");
        String key = propertyTokens[0];
        String value = "";
        if (propertyTokens.length > 1) {
            value = propertyTokens[1];
        }
        currentCommandCall.getProperties().put(key, value);
        return currentCommandCall;
    }

    private void executeCommand(CommandCall commandCall) {
        for (MavenProject currentProject : commandCall.getProjets()) {
            try {
                session.getExecutionProperties().putAll(
                        commandCall.getProperties());
                session.setCurrentProject(currentProject);
                MavenExecutionRequest request = new DefaultMavenExecutionRequest(
                        session.getLocalRepository(), session.getSettings(),
                        session.getEventDispatcher(),
                        commandCall.getCommands(), userDir.getPath(),
                        new DefaultProfileManager(embedder.getContainer(),
                                new Properties()), session
                                .getExecutionProperties(), project
                                .getProperties(), true);
                request.setPomFile(new File(currentProject.getBasedir(),
                        "pom.xml").getPath());
                embeddedMaven.execute(request);
            } catch (Exception e) {
                getLog().error(
                        "Failed to execute '" + commandCall.getCommands()
                                + "' on '" + currentProject.getArtifactId()
                                + "'");
            }
        }
    }

    private static class CommandCall {
        private final List<String> commands;

        private final List<MavenProject> projects;

        private final Properties properties;

        public CommandCall() {
            commands = new ArrayList<String>();
            projects = new ArrayList<MavenProject>();
            properties = new Properties();
        }

        public List<MavenProject> getProjets() {
            return projects;
        }

        public List<String> getCommands() {
            return commands;
        }

        public Properties getProperties() {
            return properties;
        }
    }
}
