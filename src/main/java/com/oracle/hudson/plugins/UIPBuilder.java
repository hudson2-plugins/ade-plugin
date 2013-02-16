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
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * {@link Builder} that integrates UIP commands into Hudson
 * 
 * @jamclark
 */
public class UIPBuilder extends Builder {
	private static final String newLabel = "New_Label";
	public static final String seriesName = "Series_Name";
    private final String task;
    
    // TODO: This should actualy be a list of UIP params
    private final String labelProductPrimarySchema;
    private final String labelProductComboSchemas;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public UIPBuilder(String task, String labelProductPrimarySchema, 
        String labelProductComboSchemas) {
        this.task = task;
        this.labelProductPrimarySchema = labelProductPrimarySchema;
        this.labelProductComboSchemas = labelProductComboSchemas;
    }
    
    public UIPBuilder(String task) {
        this(task, null, null);
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getTask() {
        return task;
    }

    public String getLabelProductPrimarySchema() {
        return labelProductPrimarySchema;
    }

    public String getLabelProductComboSchemas() {
        return labelProductComboSchemas;
    }
    
    /**
     * if the job contains a UIP task which calls either integrate or prebuild then we should schedule an action
     * for the ADE BuildWrapper to execute (updating the intg files)
     * This is a pattern for having a Builder task schedule work for the BuilderWrapper.setup to do on it's behalf
     */
    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
    	if ("prebuild".equals(task) || "integrate".equals(task) 
                || "sourceOnlyLabel".equals(task)) {
    		build.addAction(new RefreshIntgAction());
    	}
    	return true;
    }
    
	private String getExpandedPath(@SuppressWarnings("rawtypes") AbstractBuild build, TaskListener listener, String v) {
		try {
			return build.getEnvironment(listener).expand(v);
		} catch (IOException e) {
			listener.error("IOException while trying to expand "+v);
			e.printStackTrace();
		} catch (InterruptedException e) {
			listener.error("InterruptedException while trying to expand "+v);
			e.printStackTrace();
		}
		return v;
	}

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
    	try {
        	EnvVars envVars = build.getEnvironment(listener);
        	String label = null;
    		
        	// this is a special syntax that Hudson employs to allow us to prepend entries to the base PATH in 
    		// an OS-specific manner
    		// overrides.put("PATH+INTG0","/usr/local/packages/intg/bin");
    		envVars.put("PATH+INTG",getExpandedPath(build,listener,"$INTG_ROOT/bin"));
    		listener.getLogger().println(
    			"UIPBuilder prepending Launcher PATH with "
    			+envVars.get("PATH+INTG")+" which should over-ride any other UIP install except one in setup_env.pl");

        	if (!envVars.containsKey(newLabel)) {
        		label = getNewLabel(envVars,listener,build);
        		if (label==null) {
        			throw new AbortException("builder has no series configured");
        		}
        		listener.getLogger().println("initialize new environment variable for "+newLabel+":  "+label);
        		envVars.put(newLabel, label);
        	} else {
        		label = envVars.get(newLabel);
        		listener.getLogger().println("use existing "+newLabel+" already set by ADE plugin:  "+label);
        	}
            
            // Build UIP command
            
        	List<String> args = new ArrayList<String>();
            args.add("integrate");
            args.add("-t");
            if (task.equals("sourceOnlyLabel")) {
                args.add("integrate");
                args.add("--Source_only_label");
                args.add("1");
            } else {
                args.add(task);
            }
            args.add("-N");
            args.add("exitifnotransactions");
    		args.add("-N");
    		args.add("openlog");
    		args.add("--New_Label");
    		args.add(label);        			
        	
        	if ("prebuild".equals(task)) {
        		args.add("-N");
        		args.add("refreshview");
        	}
            
            if ("sourceOnlyLabel".equals(task)) {
                if ( (labelProductPrimarySchema != null 
                        && labelProductPrimarySchema.length() > 0) &&
                     (labelProductComboSchemas != null 
                        && labelProductComboSchemas.length() > 0) ) {
                    args.add("--Label_Product_Primary_Schema");
                    args.add(labelProductPrimarySchema);
                    args.add("--Label_Product_Combo_Schemas");
                    args.add(labelProductComboSchemas);
                }
            }
            
            // End of Build UIP command
            
            if (! ("build".equals(task) && "postpublish".equals(task)) && "integrate".equals(task)) {
               if (launcher instanceof AdeViewLauncherDecorator.UseViewLauncher) {
                   ((AdeViewLauncherDecorator.UseViewLauncher) launcher).setUseNoEnv(true);
               }
            }
            
        	listener.getLogger().print("UIP command:  (");
        	for (String a: args) {
        		listener.getLogger().print(a+" ");
        	}
        	listener.getLogger().println(")");
		
        	ProcStarter procStarter = launcher.launch()
				.envs(envVars)
				.cmds(args.toArray(new String[]{}))
				.stdout(listener)
				.stderr(listener.getLogger());
			
			Proc proc = launcher.launch(procStarter);

            
            if (launcher instanceof AdeViewLauncherDecorator.UseViewLauncher) {
                ((AdeViewLauncherDecorator.UseViewLauncher) launcher).setUseNoEnv(false);
            }
            
			int exitCode = proc.join();
			if (exitCode==0) {
				return true;
			}
		} catch (Exception e) {
			listener.fatalError("unexpected Exception caught launching UIP:  "+e.getMessage());
			(new RaiseServiceRequestAction(build, launcher, listener, "unable to run UIP command")).execute();
			for (StackTraceElement ste: e.getStackTrace()) {
				listener.error(ste.toString());
			}
			e.printStackTrace();
		}
        return false;
    }
    
    public String getNewLabel(EnvVars envVars,BuildListener listener, AbstractBuild<?, ?> build)
    	throws Exception {
    	final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyMMdd.HHmm");
    	if (envVars.containsKey(UIPBuilder.seriesName)) {
    		String series = envVars.get(seriesName);
    		String newLabel = series+"_"+dateFormatter.format(new Date());
    		return newLabel+".S";
    	} else {
    		listener.error("no "+seriesName+" in environment");
    		throw new Exception("no "+seriesName+" in environment - not continuing with integration");
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
        
        public FormValidation doCheckLabelProductPrimarySchema(@QueryParameter String value) {
            if (value != null && value.length() > 0
                    && !value.matches("[A-Z][A-Z0-9]*")) {
                return FormValidation.error("Must be an alpha numeric value in upper case. No spaces allowed.");
            }
            return FormValidation.ok();
        }
        
        public FormValidation doCheckLabelProductComboSchemas(@QueryParameter String value) {
            if (value != null && value.length() > 0 && (value.contains(" ") ||
                 !value.matches("[A-Z][A-Z0-9,]*"))) {
                return FormValidation.error("Must be a comma separated list of"
                        + " schema names in upper case. No spaces allowed.");
            }
            return FormValidation.ok();
        }
    }
}

