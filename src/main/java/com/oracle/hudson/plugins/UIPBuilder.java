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
 * {@link Builder} that integrates UIP commands into Hudson
 * 
 * @jamclark
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
    
    /**
     * if the job contains a UIP task which calls either integrate or prebuild then we should schedule an action
     * for the ADE BuildWrapper to execute (updating the intg files)
     * This is a pattern for having a Builder task schedule work for the BuilderWrapper.setup to do on it's behalf
     */
    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
    	if ("prebuild".equals(task) || "integrate".equals(task)) {
    		build.addAction(new RefreshIntgAction());
    	}
    	return true;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
    	try {
        	EnvVars envVars = build.getEnvironment(listener);
        	String label = null;
        	if (!envVars.containsKey(newLabel) || "JDEVADF_PT.POC2_GENERIC".equals(envVars.get(seriesName))) {
        		label = getNewLabel(envVars,listener,build);
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
    
    public String getNewLabel(EnvVars envVars,BuildListener listener, AbstractBuild<?, ?> build) {
    	final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyMMdd.HHmm");
    	if (envVars.containsKey(UIPBuilder.seriesName)) {
    		String series = envVars.get(seriesName);
    		String newLabel = series+"_"+dateFormatter.format(new Date());
    		if (series.equals("JDEVADF_PT.POC2_GENERIC")) {
    			return newLabel+"."+String.format("%04d",build.getNumber())+".S";
    		} else {
    			return newLabel+".S";
    		}
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

