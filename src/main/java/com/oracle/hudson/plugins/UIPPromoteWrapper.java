package com.oracle.hudson.plugins;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.InvisibleAction;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;

public class UIPPromoteWrapper extends BuildWrapper {

	@DataBoundConstructor
	public UIPPromoteWrapper() {
	}
	
	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		listener.getLogger().println("add PromoteAction to build");
		if (!build.getProject().getName().endsWith("build")) {
			listener.getLogger().println("WARNING!!!! Why has DO promotion been enabled in a job that does not have the build suffix?");
		}
		build.addAction(new PromotionAction());
		return new Environment() {
			@Override
			public boolean tearDown(AbstractBuild build, BuildListener listener)
					throws IOException, InterruptedException {
				return true;
			}
		};
	}
	
	@Override
	public Descriptor<BuildWrapper> getDescriptor() {
		return (DescriptorImpl)super.getDescriptor();
	}
	
	protected boolean promote(AbstractBuild build, Launcher launcher, BuildListener listener) {
		UIPBuilder builder = new UIPBuilder("promote");
		try {
			return builder.perform(build, launcher, listener);
		} catch (Exception e) {
			listener.fatalError("UIP promotion failed:  this job should not continue");
			return false;
		}
	}
	
	public class PromotionAction extends InvisibleAction {
		public void execute(AbstractBuild<?,?> build, Launcher launcher,
				BuildListener listener) throws Exception {
			if (!promote(build,launcher,listener)) {
				throw new Exception("unable to complete UIP promotion");
			}
		}
	}
	
	/*
	 * not used at present because we can't influence ordering and we might run promotion
	 * _after_ tearing down the ADE view
	 */
	public class PromotionEnvironment extends Environment {
		private final Launcher launcher;
		
		PromotionEnvironment(Launcher l) {
			this.launcher = l;
		}
		@Override
		public boolean tearDown(AbstractBuild build, BuildListener listener)
				throws IOException, InterruptedException {
			return promote(build,launcher,listener);
		}
	}
	
	@Extension
	public static class DescriptorImpl extends BuildWrapperDescriptor {

		@Override
		public boolean isApplicable(AbstractProject<?, ?> arg0) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "DO Promotion Enabled";
		}
	}
}
