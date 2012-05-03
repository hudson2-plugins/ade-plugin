package com.oracle.hudson.plugins;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Sample {@link Builder}.
 *
 */
public class UIPBuilder extends Builder {
	private static final String newLabel = "New_Label";
	public static final String seriesName = "Series_Name";
    private final String task;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public UIPBuilder(String task) {
        this.task = task;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getTask() {
        return task;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
    	try {
        	EnvVars envVars = build.getEnvironment(listener);
        	String label = null;
        	if (!envVars.containsKey(newLabel)) {
        		label = getNewLabel(envVars,listener);
        		if (label==null) {
        			throw new AbortException("builder has no series configured");
        		}
        		listener.getLogger().println("initialize new environment variable for "+newLabel+":  "+label);
        		envVars.put(newLabel, label);
        	} else {
        		label = envVars.get(newLabel);
        		listener.getLogger().println("use existing "+newLabel+":  "+label);
        	}
			ProcStarter procStarter = launcher.launch().cmds(
				"integrate",
				"-t",
				task,
				"-N",
				"exitifnotransactions",
				"-N",
				"openlog",
				"--Ade_Refreshview_Delay",
				"360",
				"--New_Label",
				label
				).stdout(listener).stderr(listener.getLogger());
			Proc proc = launcher.launch(procStarter);
			int exitCode = proc.join();
			if (exitCode==0) {
				return true;
			}
		} catch (IOException e) {
			listener.fatalError(e.getMessage());
		} catch (InterruptedException e) {
			listener.fatalError(e.getMessage());
		}
        return false;
    }
    
    public String getNewLabel(EnvVars envVars,BuildListener listener) {
    	final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyMMdd.HHmm");
    	if (envVars.containsKey(UIPBuilder.seriesName)) {
    		String series = envVars.get(seriesName);
    		return series+"_"+dateFormatter.format(new Date());
    	} else {
    		listener.error("no "+seriesName+" in environment");
    		for (Map.Entry<String,String> entry: envVars.entrySet()) {
    			listener.getLogger().println(entry.getKey()+":"+entry.getValue());
    		}
    		return "JRF_PT.POC1_GENERIC_"+dateFormatter.format(new Date());
    	}
    }

    // overrided for better type safety.
    // if your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link UIPBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>views/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // this marker indicates Hudson that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @SuppressWarnings("rawtypes")
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Run a UIP task in an ADE view";
        }
    }
}

