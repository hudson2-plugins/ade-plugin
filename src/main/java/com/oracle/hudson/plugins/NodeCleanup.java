package com.oracle.hudson.plugins;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.TaskListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.model.labels.LabelAtom;
import hudson.model.listeners.RunListener;
import hudson.util.RemotingDiagnostics;

import java.io.IOException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

@Extension
public class NodeCleanup extends RunListener<Run> implements Describable<NodeCleanup> {
	
	@Override
	public void onCompleted(Run r, TaskListener listener) {
	}
	@Override
	public void onDeleted(Run r) {
	}
	
	/**
	 * for the node cleanup, we use the onFinalized because we don't want to be part of the build logs for the job
	 * we might be cleaning up.  We just want access to be notified that the job has completed so that we can run some
	 * diagnostics on the slave machine.  
	 */
	@Override
	public void onFinalized(Run r) {
//		try {
//			Computer computer = r.getExecutor().getOwner();
//			NodeCleanupDescriptor desc = (NodeCleanupDescriptor)getDescriptor();
//			
//			if (computer.getNode().getAssignedLabels().contains(LabelAtom.get("ade")) && desc.getEnabled()) {
				//String outputFromGroovy = RemotingDiagnostics.executeGroovy( desc.getCleanupScript(), computer.getChannel());
				//System.out.println("output:  "+outputFromGroovy);
//			} 
//		} catch (IOException e) {

			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}
	@Override
	public void onStarted(Run r, TaskListener listener) {
		
	}
	
	private String getCleanupScript() {
		return null;
	}

	public Descriptor<NodeCleanup> getDescriptor() {
		return Hudson.getInstance().getDescriptorOrDie(getClass());
	}

	@Extension
	public static class NodeCleanupDescriptor extends Descriptor<NodeCleanup> {
		private Boolean isEnabled = false;
		private String cleanupScript = "default";
		
		public NodeCleanupDescriptor() {
			load();
		}
		public Boolean getEnabled() {
			return this.isEnabled;
		}
		public void setEnabled(Boolean b) {
			this.isEnabled = b;
		}
		public String getScript() {
			return this.cleanupScript;
		}
		public void setScript(String s) {
			this.cleanupScript = s;
		}
		String getCleanupScript() {
			return this.cleanupScript;
		}
		@Override
		public String getDisplayName() {
			return "NodeCleanup";
		}
		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws hudson.model.Descriptor.FormException {
			System.out.println(json);
			System.out.println("param:  "+req.getParameter("_.nodecleanup.enabled"));
			for (Object key: req.getParameterMap().keySet()) {
				String[] vals = (String[])req.getParameterMap().get(key);
				for (String s: vals) {
					System.out.println(key+" "+s);
				}
			}
			if ("on".equals(req.getParameter("_.nodecleanup.enabled"))) {
				this.isEnabled = true;
			} else {
				this.isEnabled = false;
			}
			this.cleanupScript = req.getParameter("_.nodecleanup.script");
			save();
			return super.configure(req);
		}
	}
}
