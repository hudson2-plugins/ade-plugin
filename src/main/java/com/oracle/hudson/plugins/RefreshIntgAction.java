package com.oracle.hudson.plugins;

import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.BuildListener;
import hudson.model.InvisibleAction;
import hudson.model.AbstractBuild;

import java.io.IOException;

public class RefreshIntgAction extends InvisibleAction {

	public void execute(AbstractBuild<?,?> build, Launcher launcher,
			BuildListener listener, AdeViewLauncherDecorator ade) throws Exception {
		if (!fetchLatestIntgFilesInView(build, launcher, listener, ade)) {
			throw new Exception("unable to fetch the intg files");
		}
	}

	final protected Boolean fetchLatestIntgFilesInView(AbstractBuild<?,?> build,
			Launcher launcher, BuildListener listener, AdeViewLauncherDecorator ade) {
		
		listener.getLogger().println("About to fetch Intg files.");
		ProcStarter procStarter = launcher.launch()
				.cmds(chooseFindAndFetchIntgFilesCommand()).stdout(listener)
				.stderr(listener.getLogger()).envs(ade.getEnvOverrides(build));

		try {
			Proc proc = launcher.launch(procStarter);
			int exitCode = proc.join();
			if (exitCode != 0) {
				listener.getLogger().println("non-zero error code ("+exitCode+") while fetching files");
				launcher.kill(ade.getEnvOverrides(build));
			} else {
				return true;
			}
		} catch (IOException e) {
			listener.getLogger().println("IOException while fetching files:  "+e);
		} catch (InterruptedException e) {
			listener.getLogger().println("InterruptedException while fetching files:  "+e);
		}
		return false;
	}

	protected String[] chooseFindAndFetchIntgFilesCommand() {
		String[] command = { 
				"/usr/bin/find", "intg", "-regex",
				"'.*\\.\\(pm\\|def\\|tmpl\\)'", 
				"-exec", 
				"bash", "-c",
				"'  if ade fetch {}@@/LATEST && [ -e {}#LATEST ]; then"
				+ "   if rm -v {} ; then"
				+ "     mv -v {}#LATEST {}; "
				+ "   fi "
				+ " else"
				+ "   echo WARNING: Failed to fetch {}@@/LATEST; "
				+ " fi'",
				"\\;"
		};
		
		return command;
	}
}
