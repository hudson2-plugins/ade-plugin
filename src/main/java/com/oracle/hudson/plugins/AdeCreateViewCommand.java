package com.oracle.hudson.plugins;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

import java.util.ArrayList;
import java.util.List;

class AdeCreateViewCommand {
	
	private List<String> args = new ArrayList<String>();
	
	AdeCreateViewCommand(AbstractBuild build, BuildListener listener, Launcher launcher, AdeViewLauncherDecorator ade) {
		args.add("ade");
		args.add("createview");
		args.add("-force");
		if (ade.getIsTip()) {
			args.add("-latest");
			args.add("-series");
			args.add(ade.getSeries());
			args.add("-tip_default");
		} else if (ade.labelExists(ade.getLabel())) {
			args.add("-label");
			args.add(ade.getExpandedLabel(build,listener));
		} else {
			args.addAll(
				(new LatestPublicLabelStrategy()).getArgs(build, launcher, listener, ade) );
		}
		args.add(ade.getViewName(build));
	}

	String[] getArgs() {
		return args.toArray(new String[]{});
	}
}
