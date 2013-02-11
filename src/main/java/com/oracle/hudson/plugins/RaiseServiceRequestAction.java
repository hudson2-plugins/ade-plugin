package com.oracle.hudson.plugins;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.TopLevelItem;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.hudson.plugins.integrationview.JobSuffixes;

/**
 * http://ade.oraclecorp.com/twiki/bin/view/ADEFAQ/GenAdeQ001#cmhelp_runs_in_interactive_or_no
 * 
 * @author slim
 *
 */
public class RaiseServiceRequestAction {
	private final AbstractBuild build;
	private final Launcher launcher;
	private final BuildListener listener;
	private final String message;
	private final String assignee = "jagannath.subramanian@oracle.com";
	private final static Pattern p = Pattern.compile("(.*)_(.*)");
	
	public RaiseServiceRequestAction(AbstractBuild build, Launcher launcher, BuildListener listener, String message) {
		this.build = build;
		this.launcher = launcher;
		this.listener = listener;
		this.message = message;
	}
	
	private void disableLabelProjects() {
		Matcher m = p.matcher(build.getProject().getName());
		if (m.matches()) {
			if (Arrays.asList(JobSuffixes.vals).contains(m.group(2))) {
				for (String suffix: JobSuffixes.vals) {
					disableJob(m.group(1)+"_"+suffix);
				}
			}
		}
	}
	
	private void disableJob(String name) {
		try {
			TopLevelItem item = Hudson.getInstance().getItem(name);
			listener.getLogger().println("disable item "+item);
			((AbstractProject)item).disable();
		} catch (Exception e) {
			listener.error("ERROR:  problem disabling item "+name);
			listener.error("exception was:  "+e);
		}
	}
	
	public void execute() {
		try {
			File f = File.createTempFile("ade", "cmhelp");
			BufferedWriter writer = new BufferedWriter(new FileWriter(f));
			writer.append("requirements for SR contents TBD:  "+message);
			writer.newLine();
			writer.close();
			Boolean success = (new FilePath(f).act(new CmHelpCallable()));
			if (success) {
				listener.getLogger().println("Successfully created SR");
			} else {
				listener.error("ERROR:  unable to create SR");
			}
			disableLabelProjects();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private class CmHelpCallable implements FileCallable<Boolean> {

		public Boolean invoke(final File f, VirtualChannel channel)
				throws IOException, InterruptedException {
			try {
				return channel.call(new Callable<Boolean,Exception>(){
					public Boolean call() throws Exception {
						ProcessBuilder builder = new ProcessBuilder();
						builder.command(				
								"ade", "cmhelp", "-non_interactive",
							    "-filed_by", assignee,
					            "-one_line_summary", "HUDSON: SLC: Liberte: "+message,
					            "-info_file", f.getPath());
						Process p = builder.start();
						p.waitFor();
						return (p.exitValue()==0);
					}
				});
			} catch (Exception e) {
				listener.error("ERROR:  problem raising SR:  "+message);
				listener.error("exception was:  "+e);
			}
			return false;
		}
		
	}
}
