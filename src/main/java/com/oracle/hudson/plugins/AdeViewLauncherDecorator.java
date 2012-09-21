package com.oracle.hudson.plugins;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run.RunnerAbortedException;
import hudson.remoting.Channel;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class AdeViewLauncherDecorator extends BuildWrapper {
	
	private String viewName;
	private String series;
	private String label;
	private Boolean isTip = false;
	private Boolean shouldDestroyView = true;
	private Boolean useExistingView = false;
	private Boolean isUsingLabel = false;
	
	//private String workspace;
	//private String user;
	
	@DataBoundConstructor
	public AdeViewLauncherDecorator(String view, String series, String label, 
									Boolean isTip, Boolean shouldDestroyView,
									Boolean useExistingView) {
		this.viewName = view;
		this.series = series;
		this.isTip = isTip;
		this.label = label;
		this.isUsingLabel = labelExists(this.label);
		this.shouldDestroyView = shouldDestroyView;
		this.useExistingView = useExistingView;
	}
	
	private String getUser() {
		return ((DescriptorImpl)this.getDescriptor()).getUser();
	}
	
	private String getWorkspace() {
		return ((DescriptorImpl)this.getDescriptor()).getWorkspace();
	}
	
	private String getViewStorage() {
		return ((DescriptorImpl)this.getDescriptor()).getViewStorage();
	}
	
	public Boolean getUseExistingView() {
		return useExistingView;
	}

	public Boolean getIsTip() {
		if (this.isTip==null) {
			return false;
		}
		return this.isTip;
	}
	
	public Boolean getShouldDestroyView() {
		if (this.shouldDestroyView==null) {
			return true;
		}
		return this.shouldDestroyView;
	}
	
	public String getSeries() {
		return this.series;
	}
	
	public String getLabel() {
		return this.label;
	}
	
	private String getExpandedLabel(AbstractBuild build, TaskListener listener) {
		try {
			return build.getEnvironment(listener).expand(this.label);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return this.label;
	}
	
	public String getView() {
		return this.viewName;
	}
	
	@SuppressWarnings("rawtypes")
	protected String getViewName(AbstractBuild build) {	
		return this.viewName+"_"+build.getNumber();
	}
	
	public Boolean isUsingLabel() {
		return this.isUsingLabel;
	}
 	
	@SuppressWarnings("rawtypes")
	@Override
	public Launcher decorateLauncher(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException,
			RunnerAbortedException {
		
		FilePath workspace = build.getWorkspace();
		listener.getLogger().println("time to decorate");
		
		final Launcher outer = launcher;
		String lviewName = getViewName(build);
		if (useExistingView){
			lviewName=viewName;
		}

		final String[] prefix = new String[]{"ade","useview",lviewName,"-exec"};
		final BuildListener l = listener;
//		final String viewName = getViewName(build);
		return new Launcher(outer) {
            @Override
            public Proc launch(ProcStarter starter) throws IOException {
            	// don't prefix either createview or destroyview
            	String[] args = starter.cmds().toArray(new String[]{});
            	starter.envs(getEnvOverrides(starter.envs(),listener));
            	if (args.length>1 && (args[1].equals("createview")||args[1].equals("destroyview"))) {
            		l.getLogger().println("detected createview/destroyview");
            		return outer.launch(starter);
            	}
            	// prefix everything else
            	starter.cmds(prefix(starter.cmds().toArray(new String[]{})));
                if (starter.masks() != null) {
                    starter.masks(prefix(starter.masks()));
                }
                return outer.launch(starter);
            }

            @Override
            public Channel launchChannel(String[] cmd, OutputStream out, FilePath workDir, Map<String, String> envVars) throws IOException, InterruptedException {
            	if (cmd.length>1 && (cmd[1].equals("createview")||cmd[1].equals("destroyview"))) {
            		l.getLogger().println("detected createview/destroyview in Channel");
            		return outer.launchChannel(prefix(cmd),out,workDir,envVars);
            	}
                return outer.launchChannel(prefix(cmd),out,workDir,envVars);
            }

            @Override
            public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
                outer.kill(modelEnvVars);
            }

            private String[] prefix(String[] args) {
                //String[] newArgs = new String[args.length+prefix.length];
                String[] newArgs = new String[prefix.length+1];
                // copy prefix args into the front of the target array
                System.arraycopy(prefix,0,newArgs,0,prefix.length);
                // copy a single space-delimited String to tail of the new arg list
                System.arraycopy(new String[]{spaceDelimitedStringArg(args)},0,newArgs,prefix.length,1);
                return newArgs;
            }
            
            private String spaceDelimitedStringArg(String[] args) {
            	StringBuffer buffer = new StringBuffer();
            	String prefix = "";
            	for (String arg: args) {
            		// String replaced = Util.replaceMacro(arg, replace);
            		buffer.append(prefix+arg);
            		prefix = " ";
            	}
            	return buffer.toString();
            }

            /*
             * fyi:  masks are for "mask"ing out certain args that could contain sensitive information
             * we don't use this in ADE
             */
            private boolean[] prefix(boolean[] args) {
                boolean[] newArgs = new boolean[args.length+prefix.length];
                System.arraycopy(args,0,newArgs,prefix.length,args.length);
                return newArgs;
            }
        };
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		if (useExistingView){
			listener.getLogger().println("setup called: use existing view" + viewName);
			return new EnvironmentImpl(launcher,build);
		}
		listener.getLogger().println("setup called:  ade createview");
		
		//workspace = build.getWorkspace().getRemote(); 
		String[] commands = null;
		if (!getIsTip()) {
			if (labelExists(this.label)) {
				commands = new String [] {
						"ade",
						"createview",
						"-force",
						"-label",
						getExpandedLabel(build,listener),
						getViewName(build)
				};
			} else {
				commands = new String[] {
					"ade",
					"createview",
					"-force",
					"-latest",
					"-series",
					series,
					getViewName(build)};
			}
		} else {
			commands = new String[] {
					"ade",
					"createview",
					"-force",
					"-latest",
					"-tip_default",
					"-series",
					series,
					getViewName(build)};
		}
		ProcStarter procStarter = launcher.launch().cmds(commands).stdout(listener).stderr(listener.getLogger()).envs(getEnvOverrides());
		Proc proc = launcher.launch(procStarter);
		int exitCode = proc.join();
		if (exitCode!=0) {
			listener.getLogger().println("createview(success):  "+exitCode);
			return new EnvironmentImpl(launcher,build);
		} else {
			listener.getLogger().println("createview:  "+exitCode);
			return new EnvironmentImpl(launcher,build);
		}
	}
	
	private boolean labelExists(String label) {
		return (label!=null && !"".equals(label));
	}

	private Map<String, String> getEnvOverrides(String[] keyValuePairs,TaskListener listener) {
		Map<String,String> map = getEnvOverrides();
        if (keyValuePairs!=null) {
	        for( String keyValue: keyValuePairs ) {
	        	String[] split = keyValue.split("=");
	        	if (split.length<2) {
	        		listener.getLogger().println(keyValue+" not in the correct format");
	        	} else {
	        		map.put(split[0],split[1]);
	        	}
	        }
        }

		return map;
	}
		
	/**
	 * ADE magic that we will need to expose as plugin-level config since all 3 of these settings depend on how
	 * the slave is configured
	 * 
	 * Env overrides do not replace the base environment but augment.  In the case of the "PATH+XYZ" syntax, you can actually
	 * prepend additional entries to PATH environment variables.  This is a special syntax that is specific to Hudson.
	 * 
	 * @return
	 */
	private Map<String, String> getEnvOverrides() {
		Map<String,String> overrides = new HashMap<String,String>();
		overrides.put("ADE_SITE","ade_slc");
		overrides.put("ADE_DEFAULT_VIEW_STORAGE_LOC",getViewStorage());
		// this is a special syntax that Hudson employs to allow us to prepend entries to the base PATH in 
		// an OS-specific manner
		overrides.put("PATH+INTG","/usr/local/packages/intg/bin");
		overrides.put(UIPBuilder.seriesName,series);
		return overrides;
	}

	@SuppressWarnings("rawtypes")
	class EnvironmentImpl extends Environment {
		private Launcher launcher;
		private AbstractBuild build;
		EnvironmentImpl(Launcher launcher, AbstractBuild build) {
			this.launcher = launcher;
			this.build = build;
		}
		@Override
		public void buildEnvVars(Map<String, String> env) {
			env.put(UIPBuilder.seriesName,series);
			env.put("ADE_USER",getUser());
			env.put("VIEW_NAME",getViewName(build));
			env.put("ADE_VIEW_ROOT",getWorkspace()+"/"+build.getProject().getName()+"/"+getUser()+"_"+getViewName(build));
		}
		@Override
		public boolean tearDown(AbstractBuild build, BuildListener listener)
				throws IOException, InterruptedException {
			try {
				if (getShouldDestroyView()) {
					listener.getLogger().println("tearing down:  ade destroyview");
					ProcStarter procStarter = launcher.launch().cmds(new String[] {
						"ade",
						"destroyview",
						getViewName(build),
						"-force"}).stdout(listener).stderr(listener.getLogger()).envs(getEnvOverrides());
					Proc proc = launcher.launch(procStarter);
					int exitCode = proc.join();
					listener.getLogger().println("destroyview:  "+exitCode);
				} else {
					listener.getLogger().println("saving view");
				}
			} catch (Exception e) {
				listener.getLogger().println("Error destroying view:  "+e.getMessage());
				return false;
			}
			return true;
		}
	}

	@Extension
	public static class DescriptorImpl extends BuildWrapperDescriptor {
		private String user;
		private String workspace;
		private String viewStorage;
		
		public DescriptorImpl() {
			load();
		}
		
		@Override
		public boolean isApplicable(AbstractProject<?, ?> arg0) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "ADEBuildWrapper";
		}
		
		public String getUser() {
			return this.user;
		}
		
		public void setUser(String user) {
			this.user = user;
		}
		
		public String getWorkspace() {
			return this.workspace;
		}
		
		public void setWorkspace(String workspace) {
			this.workspace = workspace;
		}
		
		public String getViewStorage() {
			return this.viewStorage;
		}
		
		public void setViewStorage(String v) {
			this.viewStorage = v;
		}
		
		@Override
		public boolean configure(StaplerRequest req)
				throws hudson.model.Descriptor.FormException {
			this.user = req.getParameter("ade_classic.user");
			this.workspace = req.getParameter("ade_classic.workspace");
			this.viewStorage = req.getParameter("ade_classic.view_storage");
			save();
			return super.configure(req);
		}
	}
}
