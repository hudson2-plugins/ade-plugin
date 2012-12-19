package com.oracle.hudson.plugins;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
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
		return new PromotionEnvironment(launcher);
	}
	
	@Override
	public Descriptor<BuildWrapper> getDescriptor() {
		return (DescriptorImpl)super.getDescriptor();
	}
	
	public class PromotionEnvironment extends Environment {
		private final Launcher launcher;
		
		PromotionEnvironment(Launcher l) {
			this.launcher = l;
		}
		@Override
		public boolean tearDown(AbstractBuild build, BuildListener listener)
				throws IOException, InterruptedException {
			UIPBuilder builder = new UIPBuilder("promote");
			try {
				return builder.perform(build, launcher, listener);
			} catch (Exception e) {
				listener.fatalError("UIP promotion failed:  this job should not continue");
				return false;
			}
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
