package com.oracle.hudson.plugins;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.BuildListener;
import hudson.model.Result;
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

import com.oracle.hudson.plugins.IntegrationBadgeAction.Type;
//import hudson.model.Cause.*;

/**
 * Everything build step has to happen in an ADE view requires that Hudson know how to "wrap" these commands
 * to run within the context of an ADE view. 
 * 
 * @author jamclark
 *
 */
public class AdeViewLauncherDecorator extends BuildWrapper {
	
	private String viewName;
	private String series;
	private String label;
	private Boolean isTip = false;
	private Boolean shouldDestroyView = true;
	private Boolean useExistingView = false;
	private Boolean isUsingLabel = false;
	private AdeEnvironmentCache environmentCache;
	
	@DataBoundConstructor
	public AdeViewLauncherDecorator(String view, String series, String label, 
									Boolean isTip, Boolean shouldDestroyView,
									Boolean useExistingView, Boolean cacheAdeEnv) {
		this.viewName = view;
		this.series = series;
		this.isTip = isTip;
		this.label = label;
		this.isUsingLabel = labelExists(this.label);
		this.shouldDestroyView = shouldDestroyView;
		this.useExistingView = useExistingView;
		this.environmentCache = new AdeEnvironmentCache(cacheAdeEnv);
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
	
	public String getView() {
		return this.viewName;
	}
	
	@SuppressWarnings("rawtypes")
	protected String getViewName(AbstractBuild build) {
		if(useExistingView){
			return this.viewName;
		} else {
			return this.viewName+"_"+build.getNumber();
		}
	}
	
	public Boolean isUsingLabel() {
		return this.isUsingLabel;
	}
 	
	public Boolean getCacheAdeEnv() {
		return this.environmentCache.isActive();
	}
	/**
	 * this method is called every time a build step runs and allows us to decide how to
	 * wrap any call that needs to run in an ADE view.  
	 * 
	 * the launcher passed in to the setup method is _also_ decorated.  This is important 
	 * because anything that runs in the setup method will also be decorated and in ADE, it's 
	 * important that out-of-view operations be skipped.  We have put this logic in the EnvironmentImpl
	 * class but it may be better to refactor this into the decorateLauncher method where it's more obvious
	 * 
	 * Since it may be a waste of time to enter a view if you already know the environment that
	 * you should use, we may try to cache the environment and continue to use the default launcher
	 * Otherwise, we'll decorate our launcher with the ade useview functionality 
	 */
	@SuppressWarnings("rawtypes")
	@Override
	public Launcher decorateLauncher(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException,
			RunnerAbortedException {
		if (environmentCache.isActive()) {
			return launcher;
		} else {
			listener.getLogger().println("detected command launch with ADE BuildWrapper active:  decorating launcher");
			return new UseViewLauncher(launcher,
					new String[]{"ade","useview",getViewName(build),"-exec"},build);
			
		}
	}
	
	/**
	 * there is a setup phase for all job steps that run within a build wrapper.  This is called
	 * once per job.  For ADE, we use this phase to setup the view (and possibly cache the
	 * environmnt)
	 * 
	 * @return Environment Objects represent the environment that all subsequent Launchers should run in
	 */
	@SuppressWarnings("rawtypes")
	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		if (!useExistingView){
			startViewCreationAttempts(build,launcher,listener);
		}
		
		RefreshIntgAction action = build.getAction(RefreshIntgAction.class);
		if (action!=null) {
			try {
				action.execute(build,launcher,listener,this);
			} catch (Exception e) {
				listener.error("WARNING:  even though we detected the need to refresh intg.  The operation to do so has failed");
			}
		}

		// if the ADE environment should be cached, grab all the environment variables
		// and cache them in the Environment that will be passed in to each Launcher
		if (environmentCache.isActive()) {
			return environmentCache.createEnvironment(build, launcher, listener, this);
		} else {
			listener.getLogger().println("setup called: use existing view" + getViewName(build));
			return new EnvironmentImpl(launcher,build); 
		}
	}
	
	private void startViewCreationAttempts(AbstractBuild build, Launcher launcher, BuildListener listener)
		throws InterruptedException, IOException {
		int attempts = 0;
		do {
			try {
				createNewView(build, launcher, listener);
				// success
				return;
			} catch (Exception e) {
				// try again after 10 seconds
				Thread.sleep(getPauseBetweenAttempts()*1000);
				attempts++;
				listener.getLogger().println("retry attempt #"+attempts);
				continue;
			}
		} while (attempts < getMaxAttempts());

		// we were not able to create the view
		throw new IOException("abandoning the attempt to create a view of "+series+" after "+getMaxAttempts()+" failed attempts"); 
	}
	
	@SuppressWarnings("rawtypes")
	private void createNewView(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		listener.getLogger().println("setup called:  ade createview");
		
 		ProcStarter procStarter = launcher.launch()
				.cmds(
					chooseCreateViewCommand(build, launcher, listener))
				.stdout(listener)
				.stderr(listener.getLogger())
				.envs(getEnvOverrides(build));

 		Proc proc = launcher.launch(procStarter);
		int exitCode = proc.join();


		if (exitCode!=0) {
			listener.getLogger().println("createview(success):  "+exitCode);
			launcher.kill(getEnvOverrides(build));
			throw new IOException("unable to create the view.  ADE returned a non-zero error code on the createview command");
		}
	}

	/*
	 * there are 3 different ways that we might choose to create the view
	 * 1.  go to the tip (ER from Mike Gilbode)
	 * 2.  accept a possibly parameterized label as input from the job context
	 * 3.  the latest public label
	 */
	@SuppressWarnings("rawtypes")
	private String[] chooseCreateViewCommand(AbstractBuild build,
			Launcher launcher, BuildListener listener) throws IOException,
			InterruptedException {
		if (getIsTip()) {
			return new String[] {
				"ade",
				"createview",
				"-force",
				"-latest",
				"-series",
				getSeries(),
				"-tip_default",
				getViewName(build)};
		} else {
			if (labelExists(this.label)) {
				return new String [] {
					"ade",
					"createview",
					"-force",
					"-label",
					getExpandedLabel(build,listener),
					getViewName(build)
				};
			} else {
				return (new LatestPublicLabelStrategy()).getCommand(build, launcher, listener, this);
			}
		}
	}
	
	String getUser() {
		return ((DescriptorImpl)this.getDescriptor()).getUser();
	}

	String getWorkspace() {
		return ((DescriptorImpl)this.getDescriptor()).getWorkspace();
	}

	String getViewStorage() {
		return ((DescriptorImpl)this.getDescriptor()).getViewStorage();
	}
	
	String getSite() {
		return ((DescriptorImpl)this.getDescriptor()).getSite();
	}
	
	int getMaxAttempts() {
		return ((DescriptorImpl)this.getDescriptor()).getMaxAttempts();
	}
	
	int getPauseBetweenAttempts() {
		return ((DescriptorImpl)this.getDescriptor()).getPauseBetweenViewCreationAttempts();
	}

	private String getExpandedLabel(@SuppressWarnings("rawtypes") AbstractBuild build, TaskListener listener) {
		try {
			return build.getEnvironment(listener).expand(this.label);
		} catch (IOException e) {
			listener.error("IOException while trying to expand "+this.label);
			e.printStackTrace();
		} catch (InterruptedException e) {
			listener.error("InterruptedException while trying to expand "+this.label);
			e.printStackTrace();
		}
		return this.label;
	}

	private boolean labelExists(String label) {
		return (label!=null && !"".equals(label));
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
	Map<String, String> getEnvOverrides(AbstractBuild<?,?> build) {
		Map<String,String> overrides = new HashMap<String,String>();
		
		overrides.put(UIPBuilder.seriesName,getSeries());
		overrides.put("ADE_USER",getUser());
		overrides.put("VIEW_NAME",getViewName(build));
		overrides.put("ADE_VIEW_ROOT",build.getWorkspace()+"/"+getUser()+"_"+getViewName(build));
		overrides.put("ADE_SITE",getSite());
		overrides.put("ADE_DEFAULT_VIEW_STORAGE_LOC",getViewStorage());
		// this is a special syntax that Hudson employs to allow us to prepend entries to the base PATH in 
		// an OS-specific manner
		overrides.put("PATH+INTG","/usr/local/packages/intg/bin");
                overrides.put("TMPDIR", getViewStorage()+"/"+getUser()+"_"+getViewName(build)+"/TRASH");
		return overrides;
	}
	
	Map<String, String> getEnvOverrides(String[] keyValuePairs,TaskListener listener,AbstractBuild<?,?> build) {
		Map<String,String> map = getEnvOverrides(build);
        if (keyValuePairs!=null) {
	        for( String keyValue: keyValuePairs ) {
	        	String[] split = keyValue.split("=");
	        	if (split.length==2) {
	        		map.put(split[0],split[1]);
	        	}
	        }
        }
		return map;
	}
	
	/**
	 * The UseViewLauncher permits an ADE BuildWrapper to translate all commands to run
	 * within an ADE view.
	 * 
	 * It knows to delegate to the outer launcher during createview/destroyview/showlabels/useview ops
	 * 
	 * @author slim
	 *
	 */
	class UseViewLauncher extends Launcher {
		private Launcher outer;
		private String[] prefix;
		private AbstractBuild<?,?> build;
		UseViewLauncher(Launcher outer, String[] prefix, AbstractBuild<?,?> b) {
			super(outer);
			this.outer = outer;
			this.prefix = prefix;
			this.build = b;
		}
        @Override
        public Proc launch(ProcStarter starter) throws IOException {
        	// this next line is crucial because a Hudson slave may not create
        	// launchers that have the appropriate environment to run ADE
        	// So, we must augment even this environment - ADE uses .cschrc for this today
        	starter.envs(getEnvOverrides(starter.envs(),listener,this.build));

        	// don't prefix ADE commands that are intended to run outside of a view - this is massively confusing
        	String[] args = starter.cmds().toArray(new String[]{});
        	if (args.length>1 && (
        			args[1].equals("createview")||
        			args[1].equals("destroyview")||
        			args[1].equals("showlabels")||
        			args[1].equals("useview"))) {
        		listener.getLogger().println("detected createview/destroyview/showlabels/useview -> use default launcher but still override Env Vars");
        	} else {
        		// prefix everything else
        		starter.cmds(prefix(starter.cmds().toArray(new String[]{})));
        		if (starter.masks() != null) {
        			starter.masks(prefix(starter.masks()));
        		}
        	}
            return outer.launch(starter);
        }
        
        @Override
        public Channel launchChannel(String[] cmd, OutputStream out, FilePath workDir, Map<String, String> envVars) throws IOException, InterruptedException {
        	if (cmd.length>1 && (cmd[1].equals("createview")||cmd[1].equals("destroyview")||cmd[1].equals("showlabels"))) {
        		listener.getLogger().println("detected createview/destroyview in Channel");
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
		
	}

	/**
	 * This BuildWrapper always augments the environment with enough information to use ADE
	 * 
	 * It also registers a tearDown event handler to destroy the view if the view is
	 * not configured to be saved after job completion
	 * 
	 * @author slim
	 *
	 */
	@SuppressWarnings("rawtypes")
	class EnvironmentImpl extends Environment {
		private Launcher launcher;
		private AbstractBuild build;
		private Map<String,String> envMapToAdd = null;
		EnvironmentImpl(Launcher launcher, AbstractBuild build) {
			this.launcher = launcher;
			this.build = build;
		}
		public void setEnvMapToAdd(Map<String, String> envMapToAdd) {
			this.envMapToAdd = envMapToAdd;
		}
		@Override
		public void buildEnvVars(Map<String, String> env) {
			env.putAll(getEnvOverrides(build));
			if (envMapToAdd != null ){
				env.putAll(envMapToAdd);
			}
		}
		private void runPromoteIfNecessary(AbstractBuild build,BuildListener listener) {
			listener.getLogger().println("check for DO promotion:  current result is "+build.getResult());
			try {
				UIPPromoteWrapper.PromotionAction action = build.getAction(UIPPromoteWrapper.PromotionAction.class);
				if (action!=null) {
					if (build.getResult()==null || (build.getResult()!=null && build.getResult().equals(Result.SUCCESS))) {
						build.addAction(new IntegrationBadgeAction(Type.BUILD, true));
					} else {
						build.addAction(new IntegrationBadgeAction(Type.BUILD,false));
					}
					action.execute(build,launcher,listener);
					build.addAction(new IntegrationBadgeAction(Type.PROMOTE, true));
				} else {
					listener.getLogger().println("this ADE view will terminate with no DO promotion");
				}
			} catch (Exception e) {
				listener.getLogger().println("Promotion Action failed:  "+e);
				listener.getLogger().println("setting the Build Result to failed");
				build.setResult(Result.FAILURE);
				build.addAction(new IntegrationBadgeAction(Type.PROMOTE,false));
			}
		}
		
		@Override
		public boolean tearDown(AbstractBuild build, BuildListener listener)
				throws IOException, InterruptedException {
			runPromoteIfNecessary(build,listener);
			listener.getLogger().println("before tear down result:  "+build.getResult());
			try {
				if (getShouldDestroyView()) {
					listener.getLogger().println("tearing down:  ade destroyview");
					ProcStarter procStarter = launcher.launch()
						.cmds(new String[] {
							"ade",
							"destroyview",
							getViewName(build),
							"-force"})
						.stdout(listener)
						.stderr(listener.getLogger())
						.envs(getEnvOverrides(build));
					Proc proc = launcher.launch(procStarter);
					int exitCode = proc.join();
					listener.getLogger().println("destroyview:  "+exitCode);
				} else {
					listener.getLogger().println("saving view");
				}
			} catch (InterruptedException e) {
				listener.getLogger().println("WARNING:  Interrupted while destroying view:  "+e);
				return true;
			} catch (IOException e) {
				listener.getLogger().println("WARNING:  IOException while destroying view:  "+e);
				listener.getLogger().println("still marking this as a failure because the ade destroyview _MUST_ succeed here");
				return false;
			}
			listener.getLogger().println("after tear down:  returning true");
			return true;
		}
	}

	@Extension
	public static class DescriptorImpl extends BuildWrapperDescriptor {
		private String user;
		private String token;
		private String workspace;
		private String viewStorage;
		private String site;
		private int maxAttempts = 15;
		private int pauseBetweenViewCreationAttempts = 10; // in seconds
		
		public DescriptorImpl() {
			load();
		}
		
		public int getMaxAttempts() {
			return this.maxAttempts;
		}
		
		public int getPauseBetweenViewCreationAttempts() {
			return this.pauseBetweenViewCreationAttempts;
		}

		@Override
		public boolean isApplicable(AbstractProject<?, ?> arg0) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "ADEBuildWrapper";
		}
		
		public String getSite() {
			return this.site;
		}
		
		public void setSite(String s) {
			this.site = s;
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
			this.site = req.getParameter("ade_classic.site");
			this.token = req.getParameter("ade_classic.token");
			
			this.maxAttempts = Integer.parseInt(req.getParameter("ade_classic.maxattempts"));
			this.pauseBetweenViewCreationAttempts = Integer.parseInt(req.getParameter("ade_classic.pause"));
			save();
			return super.configure(req);
		}

		public String getToken() {
			return this.token;
		}
		public void setToken(String s) {
			this.token = s;
		}
	}
}
