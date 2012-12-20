package com.oracle.hudson.plugins;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.util.Map;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class RepositoryCleanupWrapper extends BuildWrapper {
	private String variableName;
	private String location;
	private String policy;
	private Boolean isCleaningRepository;
	private Boolean isOverridingRepository;
	
	@DataBoundConstructor
	public RepositoryCleanupWrapper(String v, String l, String p, Boolean b, Boolean b1) {
		this.variableName = v;
		this.location = l;
		this.policy = p;
		this.isCleaningRepository = b;
		this.isOverridingRepository = b1;
	}
	
	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		return new CleanupRepository();
	}
	
	private class CleanupRepository extends Environment {
		@Override
		public void buildEnvVars(Map<String, String> env) {
			// TODO add the repository location to use into the EnvVars
			super.buildEnvVars(env);
		}
		@Override
		public boolean tearDown(AbstractBuild build, BuildListener listener)
				throws IOException, InterruptedException {
			listener.getLogger().println("cleanup the repository after the build");
			return true;
		}
	}

	@Extension
	public static class DescriptorImpl extends BuildWrapperDescriptor {
		private enum Policy {PURGE,PURGE_UNUSED};
		private Policy policy = Policy.PURGE_UNUSED;
		private String location;
		
		public String getPolicy() {
			return policy.name();
		}

		public void setPolicy(String policy) {
			if (policy==null) {
				this.policy = Policy.PURGE;
			} else {
				this.policy = Policy.valueOf(policy);
			}
		}

		public String getLocation() {
			return location;
		}

		public void setLocation(String location) {
			this.location = location;
		}

		public DescriptorImpl() {
			load();
		}

		@Override
		public boolean isApplicable(AbstractProject<?, ?> arg0) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Farm Repository";
		}
		
		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws hudson.model.Descriptor.FormException {
			if (req.getParameter("farmcleanup.policy")!=null) {
				this.policy = Policy.valueOf(req.getParameter("farmcleanup.policy"));
			}
			if (req.getParameter("farmcleanup.location")!=null) {
				this.location = req.getParameter("farmcleanup.location");
			}
			save();
			return super.configure(req);
		}
	}
}
