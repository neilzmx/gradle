/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.invocation;

import groovy.lang.Closure;
import org.gradle.BuildAdapter;
import org.gradle.BuildListener;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.internal.GradleDistributionLocator;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.AbstractPluginAware;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.listener.ActionBroadcast;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.listener.ListenerManager;
import org.gradle.util.GradleVersion;

import javax.inject.Inject;
import java.io.File;

public class DefaultGradle extends AbstractPluginAware implements GradleInternal {
    private ProjectInternal rootProject;
    private ProjectInternal defaultProject;
    private final Gradle parent;
    private final StartParameter startParameter;
    private final ServiceRegistry services;
    private final ListenerBroadcast<BuildListener> buildListenerBroadcast;
    private final ListenerBroadcast<ProjectEvaluationListener> projectEvaluationListenerBroadcast;
    private ActionBroadcast<Project> rootProjectActions = new ActionBroadcast<Project>();

    private final ClassLoaderScope classLoaderScope;

    public DefaultGradle(Gradle parent, StartParameter startParameter, ServiceRegistryFactory parentRegistry) {
        this.parent = parent;
        this.startParameter = startParameter;
        this.services = parentRegistry.createFor(this);
        classLoaderScope = services.get(ClassLoaderScopeRegistry.class).getCoreAndPluginsScope();
        buildListenerBroadcast = getListenerManager().createAnonymousBroadcaster(BuildListener.class);
        projectEvaluationListenerBroadcast = getListenerManager().createAnonymousBroadcaster(ProjectEvaluationListener.class);
        buildListenerBroadcast.add(new BuildAdapter() {
            @Override
            public void projectsLoaded(Gradle gradle) {
                rootProjectActions.execute(rootProject);
                rootProjectActions = null;
            }
        });
    }

    @Override
    public String toString() {
        return rootProject == null ? "build" : String.format("build '%s'", rootProject.getName());
    }

    public Gradle getParent() {
        return parent;
    }

    public String getGradleVersion() {
        return GradleVersion.current().getVersion();
    }

    public File getGradleHomeDir() {
        return getDistributionLocator().getGradleHome();
    }

    public File getGradleUserHomeDir() {
        return startParameter.getGradleUserHomeDir();
    }

    public StartParameter getStartParameter() {
        return startParameter;
    }

    public ProjectInternal getRootProject() {
        if (rootProject == null) {
            throw new IllegalStateException("The root project is not yet available for " + this + ".");
        }
        return rootProject;
    }

    public void setRootProject(ProjectInternal rootProject) {
        this.rootProject = rootProject;
    }

    public void rootProject(Action<? super Project> action) {
        if (rootProjectActions != null) {
            rootProjectActions.add(action);
        } else {
            assert rootProject != null;
            action.execute(rootProject);
        }
    }

    public void allprojects(final Action<? super Project> action) {
        rootProject(new Action<Project>() {
            public void execute(Project project) {
                project.allprojects(action);
            }
        });
    }

    public ProjectInternal getDefaultProject() {
        return defaultProject;
    }

    public void setDefaultProject(ProjectInternal defaultProject) {
        this.defaultProject = defaultProject;
    }

    @Inject
    public TaskGraphExecuter getTaskGraph() {
        throw new UnsupportedOperationException();
    }

    public ProjectEvaluationListener addProjectEvaluationListener(ProjectEvaluationListener listener) {
        addListener(listener);
        return listener;
    }

    public void removeProjectEvaluationListener(ProjectEvaluationListener listener) {
        removeListener(listener);
    }

    public void beforeProject(Closure closure) {
        projectEvaluationListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("beforeEvaluate", closure));
    }

    public void afterProject(Closure closure) {
        projectEvaluationListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("afterEvaluate", closure));
    }

    public void buildStarted(Closure closure) {
        buildListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("buildStarted", closure));
    }

    public void settingsEvaluated(Closure closure) {
        buildListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("settingsEvaluated", closure));
    }

    public void projectsLoaded(Closure closure) {
        buildListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("projectsLoaded", closure));
    }

    public void projectsEvaluated(Closure closure) {
        buildListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("projectsEvaluated", closure));
    }

    public void buildFinished(Closure closure) {
        buildListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("buildFinished", closure));
    }

    public void addListener(Object listener) {
        getListenerManager().addListener(listener);
    }

    public void removeListener(Object listener) {
        getListenerManager().removeListener(listener);
    }

    public void useLogger(Object logger) {
        getListenerManager().useLogger(logger);
    }

    public ProjectEvaluationListener getProjectEvaluationBroadcaster() {
        return projectEvaluationListenerBroadcast.getSource();
    }

    public void addBuildListener(BuildListener buildListener) {
        addListener(buildListener);
    }

    public BuildListener getBuildListenerBroadcaster() {
        return buildListenerBroadcast.getSource();
    }

    public Gradle getGradle() {
        return this;
    }

    public ServiceRegistry getServices() {
        return services;
    }

    @Inject
    public ServiceRegistryFactory getServiceRegistryFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected DefaultObjectConfigurationAction createObjectConfigurationAction() {
        return new DefaultObjectConfigurationAction(getFileResolver(), getScriptPluginFactory(), getScriptHandlerFactory(), getClassLoaderScope(), this);
    }

    public ClassLoaderScope getClassLoaderScope() {
        return classLoaderScope;
    }

    @Inject
    protected ScriptHandlerFactory getScriptHandlerFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ScriptPluginFactory getScriptPluginFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected GradleDistributionLocator getDistributionLocator() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ListenerManager getListenerManager() {
        throw new UnsupportedOperationException();
    }

    @Inject
    public PluginManagerInternal getPluginManager() {
        throw new UnsupportedOperationException();
    }
}
