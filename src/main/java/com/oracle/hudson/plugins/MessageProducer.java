package com.oracle.hudson.plugins;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.listeners.RunListener;

@Extension
public class MessageProducer extends RunListener<Run> implements Describable<MessageProducer> {
	
	@Override
	public void onCompleted(Run r, TaskListener listener) {
		if (isEnabled()) {
			System.out.println("onCompleted for "+r.getBuildStatusUrl());
		}
	}
	@Override
	public void onDeleted(Run r) {
		if (isEnabled()) {
			System.out.println("onDeleted for "+r.getBuildStatusUrl());
		}
	}
	@Override
	public void onFinalized(Run r) {
		if (isEnabled()) {
			System.out.println("onDeleted for "+r.getBuildStatusUrl());
		}
	}
	@Override
	public void onStarted(Run r, TaskListener listener) {
		if (isEnabled()) {
			System.out.println("onStarted for "+r.getBuildStatusUrl());
		}
	}
	public Descriptor<MessageProducer> getDescriptor() {
		return Hudson.getInstance().getDescriptorOrDie(getClass());
	}
	
	private boolean isEnabled() {
		Boolean b = ((MessageProducerDescriptor)getDescriptor()).getEnabled();
		if (!b) {
			System.out.println("skipping event processing because the listener is disabled");
		}
		return b;
	}

	@Extension
	public static class MessageProducerDescriptor extends Descriptor<MessageProducer> {
		private Boolean isEnabled = false;
		
		public MessageProducerDescriptor() {
			load();
		}
		@Override
		public String getDisplayName() {
			return "MessageProducer";
		}
		public Boolean getEnabled() {
			return isEnabled;
		}
		public void setEnabled(Boolean b) {
			this.isEnabled = b;
		}
		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws hudson.model.Descriptor.FormException {
			if ("on".equals(req.getParameter("ade_classic.messageproducer.isEnabled"))) {
				this.isEnabled = true;
			} else {
				this.isEnabled = false;
			}
			save();
			return super.configure(req);
		}
	}
}
